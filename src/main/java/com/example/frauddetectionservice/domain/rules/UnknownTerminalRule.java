package com.example.frauddetectionservice.domain.rules;

import com.example.frauddetectionservice.domain.FraudRule;
import com.example.frauddetectionservice.domain.RiskContext;
import com.example.frauddetectionservice.domain.RuleCode;
import com.example.frauddetectionservice.domain.RuleOutcome;
import com.example.frauddetectionservice.domain.Terminal;
import com.example.frauddetectionservice.domain.Transaction;

/**
 * Catalogue rule: unknown terminal.
 *
 * <ul>
 *   <li>Unregistered terminal: a graded outcome of 100. Its weight decides whether it rejects alone.</li>
 *   <li>Registered but BLOCKED terminal: critical. Blocked terminals must never transact, so no weight applies.</li>
 * </ul>
 */
public final class UnknownTerminalRule implements FraudRule {

    private static final int ORDER = 1;

    @Override
    public RuleCode code() {
        return RuleCode.UNKNOWN_TERMINAL;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public RuleOutcome evaluate(Transaction transaction, RiskContext context) {
        if (context.terminal().isEmpty()) {
            return new RuleOutcome.Scored(code(), 100, "Terminal is not registered");
        }
        Terminal terminal = context.terminal().get();
        if (terminal.status() == Terminal.Status.BLOCKED) {
            return new RuleOutcome.Critical(code(), "Terminal is blocked");
        }
        return new RuleOutcome.Scored(code(), 0, "Terminal is registered and active");
    }
}
