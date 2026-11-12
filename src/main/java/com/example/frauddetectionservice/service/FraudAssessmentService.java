package com.example.frauddetectionservice.service;

import com.example.frauddetectionservice.config.RiskProperties;
import com.example.frauddetectionservice.entity.AssessmentEntity;
import com.example.frauddetectionservice.entity.TerminalEntity;
import com.example.frauddetectionservice.exception.DuplicateAssessmentException;
import com.example.frauddetectionservice.exception.IdempotencyKeyReusedException;
import com.example.frauddetectionservice.exception.UnsupportedCurrencyException;
import com.example.frauddetectionservice.model.Assessment;
import com.example.frauddetectionservice.model.Terminal;
import com.example.frauddetectionservice.model.Transaction;
import com.example.frauddetectionservice.repository.AssessmentRepository;
import com.example.frauddetectionservice.repository.TerminalRepository;
import com.example.frauddetectionservice.rules.RiskContext;
import com.example.frauddetectionservice.rules.RiskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates one assessment: load the terminal, count recent attempts, evaluate the rules, record the outcome.
 *
 * <p>The whole flow runs in one database transaction. The terminal row lock taken by
 * {@link TerminalRepository#findByTerminalId} serializes concurrent assessments for the same terminal, so the
 * velocity count cannot be raced past its limit. A retry that arrives while the first attempt is still running
 * waits for the lock, then sees the stored key and is refused.
 *
 * <p>Idempotency: a request with an Idempotency-Key is refused if that key was already used on the same
 * terminal. A refused retry is not assessed or recorded.
 *
 * <p>Logging: only identifiers and outcomes are logged. Amounts and card data never reach the logger.
 */
@Service
public class FraudAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(FraudAssessmentService.class);

    private final RiskEngine engine;
    private final TerminalRepository terminals;
    private final AssessmentRepository assessments;
    private final Clock clock;
    private final Duration velocityWindow;
    private final Set<String> supportedCurrencies;

    public FraudAssessmentService(
            RiskEngine engine,
            TerminalRepository terminals,
            AssessmentRepository assessments,
            Clock clock,
            RiskProperties properties) {
        this.engine = engine;
        this.terminals = terminals;
        this.assessments = assessments;
        this.clock = clock;
        this.velocityWindow = properties.velocityWindow();
        this.supportedCurrencies = Set.copyOf(properties.amountBaseLimits().keySet());
    }

    /**
     * @param idempotencyKey the caller's Idempotency-Key, or null when none was sent
     * @throws DuplicateAssessmentException if the key was already used on this terminal for the same payment
     * @throws IdempotencyKeyReusedException if the key was already used on this terminal for a different payment
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Assessment assess(Transaction transaction, String idempotencyKey) {
        String currencyCode = transaction.currency().getCurrencyCode();
        if (!supportedCurrencies.contains(currencyCode)) {
            throw new UnsupportedCurrencyException(currencyCode);
        }

        // Read the terminal (and take the lock) first, then check the key and the clock. Checking the key
        // before the lock would let two identical retries both pass it.
        Optional<Terminal> terminal = terminals.findByTerminalId(transaction.terminalId()).map(TerminalEntity::toDomain);

        String fingerprint = idempotencyKey == null ? null : fingerprint(transaction);
        if (idempotencyKey != null) {
            Optional<AssessmentEntity> earlier =
                    assessments.findByTerminalIdAndIdempotencyKey(transaction.terminalId(), idempotencyKey);
            if (earlier.isPresent()) {
                if (fingerprint.equals(earlier.get().getRequestFingerprint())) {
                    log.info("assessment refused as duplicate terminalId={}", transaction.terminalId());
                    throw new DuplicateAssessmentException();
                }
                log.info("assessment refused, key reused terminalId={}", transaction.terminalId());
                throw new IdempotencyKeyReusedException();
            }
        }

        Instant now = clock.instant();
        long priorAttempts = assessments.countByTerminalSince(transaction.terminalId(), now.minus(velocityWindow));

        Assessment assessment = engine.assess(transaction, new RiskContext(terminal, priorAttempts));

        assessments.save(new AssessmentEntity(
                UUID.randomUUID(),
                transaction.terminalId(),
                transaction.amount(),
                transaction.currency(),
                transaction.terminalThreatScore(),
                assessment.rejected(),
                assessment.fraudScore(),
                now,
                idempotencyKey,
                fingerprint));

        log.info("assessment terminalId={} decision={} fraudScore={} rules={}",
                transaction.terminalId(),
                assessment.rejected() ? "REJECT" : "ALLOW",
                assessment.fraudScore(),
                assessment.triggeredRules().stream().map(r -> r.code().name()).toList());

        return assessment;
    }

    /**
     * Identifies a payment without storing it: terminal, amount, currency and threat score. The card number is
     * not included because it is not kept, so a key reused with only a different card is not detected.
     */
    private static String fingerprint(Transaction transaction) {
        String canonical = String.join("|",
                transaction.terminalId(),
                transaction.amount().stripTrailingZeros().toPlainString(),
                transaction.currency().getCurrencyCode(),
                Integer.toString(transaction.terminalThreatScore()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
