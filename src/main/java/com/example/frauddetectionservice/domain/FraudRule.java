package com.example.frauddetectionservice.domain;

/**
 * Strategy interface for a single risk rule. Implementations are pure: they read the transaction and the
 * pre-loaded {@link RiskContext} and never touch I/O, which keeps them deterministic and easy to test.
 */
public interface FraudRule {

    RuleCode code();

    /** Evaluation order. Lower values run first, so critical and cheap rules go at the front. */
    int order();

    RuleOutcome evaluate(Transaction transaction, RiskContext context);
}
