package com.example.frauddetectionservice.model;

import com.example.frauddetectionservice.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An in-flight card transaction, validated at construction so no invalid state reaches the rules.
 * The card number is validated at the boundary and not carried here, because no rule uses it.
 */
public record Transaction(
        BigDecimal amount,
        Currency currency,
        String terminalId,
        int terminalThreatScore) {

    public Transaction {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(terminalId, "terminalId");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        // Stored and evaluated values must match: reject precision the currency cannot represent.
        if (amount.stripTrailingZeros().scale() > currency.getDefaultFractionDigits()) {
            throw new InvalidAmountException();
        }
        if (terminalId.isBlank()) {
            throw new IllegalArgumentException("terminalId must not be blank");
        }
        if (terminalThreatScore < 0 || terminalThreatScore > 100) {
            throw new IllegalArgumentException("terminalThreatScore must be within 0..100");
        }
    }
}
