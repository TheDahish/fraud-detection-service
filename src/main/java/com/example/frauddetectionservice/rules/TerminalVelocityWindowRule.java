package com.example.frauddetectionservice.rules;

import com.example.frauddetectionservice.model.Terminal;
import com.example.frauddetectionservice.model.Transaction;

/**
 * Catalogue rule: too many transactions for a terminal within a time window.
 *
 * <p>Attempts include rejected ones, because a burst of rejected attempts is itself a fraud signal.
 * Contribution: 0 up to {@code maxAttempts}, then {@code 50 + 50 × excess / maxAttempts} capped at 100.
 */
public final class TerminalVelocityWindowRule implements FraudRule {

    private static final int ORDER = 3;

    private final int maxAttempts;

    public TerminalVelocityWindowRule(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public RuleCode code() {
        return RuleCode.TERMINAL_VELOCITY_WINDOW;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public RuleOutcome evaluate(Transaction transaction, RiskContext context) {
        long attempts = context.attemptsIncludingCurrent();
        if (attempts <= maxAttempts) {
            return new RuleOutcome.Scored(code(), 0, "Terminal is within the velocity window limit");
        }
        long excess = attempts - maxAttempts;
        int contribution = (int) Math.min(100L, 50L + (50L * excess) / maxAttempts);
        return new RuleOutcome.Scored(code(), contribution, "Too many attempts for this terminal within the time window");
    }
}
