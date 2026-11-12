package com.example.frauddetectionservice.model;

import com.example.frauddetectionservice.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static Transaction tx(String amount, String currency, String terminalId, int threat) {
        return new Transaction(new BigDecimal(amount), Currency.getInstance(currency), terminalId, threat);
    }

    @Test
    void amountWithinCurrencyMinorUnitsIsAccepted() {
        assertThatCode(() -> tx("10.50", "EUR", "T-1001", 0)).doesNotThrowAnyException();
        assertThatCode(() -> tx("10.00", "JPY", "T-1001", 0)).doesNotThrowAnyException();
    }

    @Test
    void amountWithMoreDecimalsThanCurrencyAllowsIsRejected() {
        assertThatThrownBy(() -> tx("10.5", "JPY", "T-1001", 0)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> tx("0.001", "EUR", "T-1001", 0)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> tx("0", "EUR", "T-1001", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx("-1", "EUR", "T-1001", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void threatScoreOutsideRangeIsRejected() {
        assertThatThrownBy(() -> tx("1", "EUR", "T-1001", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx("1", "EUR", "T-1001", 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankTerminalIdIsRejected() {
        assertThatThrownBy(() -> tx("1", "EUR", " ", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
