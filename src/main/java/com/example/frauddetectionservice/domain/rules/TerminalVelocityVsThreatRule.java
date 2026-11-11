package com.example.frauddetectionservice.domain.rules;

import com.example.frauddetectionservice.domain.FraudRule;
import com.example.frauddetectionservice.domain.RiskContext;
import com.example.frauddetectionservice.domain.RuleCode;
import com.example.frauddetectionservice.domain.RuleOutcome;
import com.example.frauddetectionservice.domain.Transaction;

/**
 * Catalogue rule: too many transactions for a terminal compared with its threat score.
 *
 * <p>The allowed count shrinks as the threat score rises:
 * {@code allowed = max(1, baseAllowed × (100 − threatScore) / 100)}.
 * Contribution: 0 up to {@code allowed}, then {@code 50 + 50 × excess / allowed} capped at 100.
 */
public final class TerminalVelocityVsThreatRule implements FraudRule {

    private static final int ORDER = 4;

    private final int baseAllowedAttempts;

    public TerminalVelocityVsThreatRule(int baseAllowedAttempts) {
        if (baseAllowedAttempts < 1) {
            throw new IllegalArgumentException("baseAllowedAttempts must be at least 1");
        }
        this.baseAllowedAttempts = baseAllowedAttempts;
    }

    @Override
    public RuleCode code() {
        return RuleCode.TERMINAL_VELOCITY_VS_THREAT;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public RuleOutcome evaluate(Transaction transaction, RiskContext context) {
        long allowed = Math.max(1L, (long) baseAllowedAttempts * (100 - transaction.terminalThreatScore()) / 100);
        long attempts = context.attemptsIncludingCurrent();
        if (attempts <= allowed) {
            return new RuleOutcome.Scored(code(), 0, "Terminal attempts are within the threat-adjusted limit");
        }
        long excess = attempts - allowed;
        int contribution = (int) Math.min(100L, 50L + (50L * excess) / allowed);
        return new RuleOutcome.Scored(code(), contribution, "Attempts exceed the limit allowed for the terminal threat score");
    }
}
