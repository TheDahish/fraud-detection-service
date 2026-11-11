package com.example.frauddetectionservice.domain.rules;

import com.example.frauddetectionservice.domain.FraudRule;
import com.example.frauddetectionservice.domain.RiskContext;
import com.example.frauddetectionservice.domain.RuleCode;
import com.example.frauddetectionservice.domain.RuleOutcome;
import com.example.frauddetectionservice.domain.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * Catalogue rule: amount too large for the terminal's threat score.
 *
 * <p>The per-terminal limit shrinks as the threat score rises:
 * {@code limit = baseLimit × (100 − threatScore) / 100}. A threat score of 100 leaves no headroom.
 *
 * <ul>
 *   <li>Above {@code baseLimit × ceilingMultiplier}: critical, rejected regardless of other signals.</li>
 *   <li>Above the limit but under the ceiling: graded, {@code 50 × amount / limit} capped at 100
 *       (50 just over the limit, 100 at twice the limit).</li>
 * </ul>
 */
public final class AmountVsThreatScoreRule implements FraudRule {

    private static final int ORDER = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RATIO_SCALE = 6;

    private final Map<String, BigDecimal> baseLimits;
    private final BigDecimal ceilingMultiplier;

    /**
     * @param baseLimits        base limit per ISO 4217 code, in major units
     * @param ceilingMultiplier multiple of the base limit above which the transaction is critical
     */
    public AmountVsThreatScoreRule(Map<String, BigDecimal> baseLimits, BigDecimal ceilingMultiplier) {
        this.baseLimits = Map.copyOf(Objects.requireNonNull(baseLimits, "baseLimits"));
        this.ceilingMultiplier = Objects.requireNonNull(ceilingMultiplier, "ceilingMultiplier");
    }

    @Override
    public RuleCode code() {
        return RuleCode.AMOUNT_VS_THREAT_SCORE;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public RuleOutcome evaluate(Transaction transaction, RiskContext context) {
        BigDecimal base = baseLimitFor(transaction);
        BigDecimal amount = transaction.amount();

        if (amount.compareTo(base.multiply(ceilingMultiplier)) > 0) {
            return new RuleOutcome.Critical(code(), "Amount exceeds the hard ceiling for this currency");
        }

        BigDecimal limit = base.multiply(BigDecimal.valueOf(100L - transaction.terminalThreatScore())).divide(HUNDRED);
        if (amount.compareTo(limit) <= 0) {
            return new RuleOutcome.Scored(code(), 0, "Amount is within the terminal limit");
        }
        if (limit.signum() == 0) {
            return new RuleOutcome.Scored(code(), 100, "Terminal threat score leaves no amount headroom");
        }

        BigDecimal ratio = amount.divide(limit, RATIO_SCALE, RoundingMode.HALF_UP);
        int contribution = ratio.multiply(BigDecimal.valueOf(50))
                .setScale(0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100))
                .intValue();
        return new RuleOutcome.Scored(code(), contribution,
                "Amount exceeds the limit allowed for the terminal threat score");
    }

    private BigDecimal baseLimitFor(Transaction transaction) {
        BigDecimal base = baseLimits.get(transaction.currency().getCurrencyCode());
        if (base == null) {
            // The application layer rejects unsupported currencies first, so reaching this is a wiring bug.
            throw new IllegalStateException("No base limit configured for currency " + transaction.currency().getCurrencyCode());
        }
        return base;
    }
}
