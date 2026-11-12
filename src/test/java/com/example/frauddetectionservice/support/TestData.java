package com.example.frauddetectionservice.support;

import com.example.frauddetectionservice.model.Terminal;
import com.example.frauddetectionservice.model.Transaction;
import com.example.frauddetectionservice.rules.RiskContext;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

public final class TestData {

    private TestData() {
    }

    public static Transaction tx(String amount, int threat) {
        return tx(amount, "EUR", "T-1001", threat);
    }

    public static Transaction tx(String amount, String currency, String terminalId, int threat) {
        return new Transaction(new BigDecimal(amount), Currency.getInstance(currency), terminalId, threat);
    }

    public static Terminal active(String id) {
        return new Terminal(id, Terminal.Status.ACTIVE);
    }

    public static RiskContext ctx(Terminal terminal, long priorAttempts) {
        return new RiskContext(Optional.ofNullable(terminal), priorAttempts);
    }

    public static RiskContext unknownTerminal(long priorAttempts) {
        return new RiskContext(Optional.empty(), priorAttempts);
    }
}
