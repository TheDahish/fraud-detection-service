package com.example.frauddetectionservice.support;

import com.example.frauddetectionservice.domain.CardNumber;
import com.example.frauddetectionservice.domain.RiskContext;
import com.example.frauddetectionservice.domain.Terminal;
import com.example.frauddetectionservice.domain.Transaction;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

public final class TestData {

    public static final String CARD = "4111111111111111";

    private TestData() {
    }

    public static Transaction tx(String amount, int threat) {
        return tx(amount, "EUR", "T-1001", threat);
    }

    public static Transaction tx(String amount, String currency, String terminalId, int threat) {
        return new Transaction(new BigDecimal(amount), Currency.getInstance(currency), terminalId, threat,
                CardNumber.of(CARD));
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
