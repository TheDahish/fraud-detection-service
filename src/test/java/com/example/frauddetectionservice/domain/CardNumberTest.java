package com.example.frauddetectionservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardNumberTest {

    @Test
    void acceptsLuhnValidNumber() {
        assertThat(CardNumber.of("4111111111111111").unmaskedValue()).isEqualTo("4111111111111111");
    }

    @Test
    void rejectsBadCheckDigit() {
        assertThatThrownBy(() -> CardNumber.of("4111111111111112"))
                .isInstanceOf(InvalidCardNumberException.class);
    }

    @Test
    void rejectsNonDigitsAndWrongLengths() {
        assertThatThrownBy(() -> CardNumber.of("4111-1111-1111-1111")).isInstanceOf(InvalidCardNumberException.class);
        assertThatThrownBy(() -> CardNumber.of("41111111111")).isInstanceOf(InvalidCardNumberException.class);
        assertThatThrownBy(() -> CardNumber.of("41111111111111111111")).isInstanceOf(InvalidCardNumberException.class);
        assertThatThrownBy(() -> CardNumber.of(null)).isInstanceOf(InvalidCardNumberException.class);
    }

    @Test
    void toStringNeverContainsDigits() {
        assertThat(CardNumber.of("4111111111111111").toString()).doesNotContainPattern("\\d");
    }

    @Test
    void validationMessageDoesNotEchoInput() {
        assertThatThrownBy(() -> CardNumber.of("4111111111111112"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("4111"));
    }
}
