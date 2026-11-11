package com.example.frauddetectionservice.application;

import com.example.frauddetectionservice.application.port.AssessmentLedger;
import com.example.frauddetectionservice.application.port.AssessmentRecord;
import com.example.frauddetectionservice.application.port.CardTokenizer;
import com.example.frauddetectionservice.application.port.TerminalRepository;
import com.example.frauddetectionservice.config.RiskProperties;
import com.example.frauddetectionservice.domain.Assessment;
import com.example.frauddetectionservice.domain.RiskContext;
import com.example.frauddetectionservice.domain.RiskEngine;
import com.example.frauddetectionservice.domain.Terminal;
import com.example.frauddetectionservice.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates one assessment: load facts, evaluate rules, persist the outcome.
 *
 * <p>The whole flow runs in one database transaction. The terminal row lock taken by
 * {@link TerminalRepository#findForUpdate} serializes concurrent assessments for the same terminal, so the
 * velocity count cannot be raced past its limit.
 *
 * <p>Logging: only identifiers and outcomes are logged. Amounts and card data never reach the logger.
 */
@Service
public class FraudAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(FraudAssessmentService.class);

    private final RiskEngine engine;
    private final TerminalRepository terminals;
    private final AssessmentLedger ledger;
    private final CardTokenizer cardTokenizer;
    private final Clock clock;
    private final Duration velocityWindow;
    private final Set<String> supportedCurrencies;

    public FraudAssessmentService(
            RiskEngine engine,
            TerminalRepository terminals,
            AssessmentLedger ledger,
            CardTokenizer cardTokenizer,
            Clock clock,
            RiskProperties properties) {
        this.engine = engine;
        this.terminals = terminals;
        this.ledger = ledger;
        this.cardTokenizer = cardTokenizer;
        this.clock = clock;
        this.velocityWindow = properties.velocityWindow();
        this.supportedCurrencies = Set.copyOf(properties.amountBaseLimits().keySet());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Assessment assess(Transaction transaction) {
        String currencyCode = transaction.currency().getCurrencyCode();
        if (!supportedCurrencies.contains(currencyCode)) {
            throw new UnsupportedCurrencyException(currencyCode);
        }

        // Read the terminal (and take the lock) first, then the clock. Capturing time before the lock wait
        // would let the window be computed from a stale instant.
        Optional<Terminal> terminal = terminals.findForUpdate(transaction.terminalId());
        Instant now = clock.instant();
        long priorAttempts = ledger.countAttemptsSince(transaction.terminalId(), now.minus(velocityWindow));

        Assessment assessment = engine.assess(transaction, new RiskContext(terminal, priorAttempts));

        ledger.record(new AssessmentRecord(
                UUID.randomUUID(),
                transaction.terminalId(),
                transaction.amount(),
                transaction.currency(),
                transaction.terminalThreatScore(),
                cardTokenizer.tokenize(transaction.cardNumber()),
                assessment.rejected(),
                assessment.fraudScore(),
                now));

        log.info("assessment terminalId={} decision={} fraudScore={} rules={}",
                transaction.terminalId(),
                assessment.rejected() ? "REJECT" : "ALLOW",
                assessment.fraudScore(),
                assessment.triggeredRules().stream().map(r -> r.code().name()).toList());

        return assessment;
    }
}
