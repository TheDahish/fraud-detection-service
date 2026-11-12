package com.example.frauddetectionservice.util;

import com.example.frauddetectionservice.exception.InvalidCardNumberException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardNumberValidatorTest {

    @Test
    void acceptsLuhnValidNumber() {
        assertThatCode(() -> CardNumberValidator.requireValid("4111111111111111")).doesNotThrowAnyException();
    }

    @Test
    void rejectsBadCheckDigit() {
        assertThatThrownBy(() -> CardNumberValidator.requireValid("4111111111111112"))
                .isInstanceOf(InvalidCardNumberException.class);
    }

    @Test
    void rejectsNonDigitsAndWrongLengths() {
        assertThatThrownBy(() -> CardNumberValidator.requireValid("4111-1111-1111-1111")).isInstanceOf(InvalidCardNumberException.class);
        assertThatThrownBy(() -> CardNumberValidator.requireValid("41111111111")).isInstanceOf(InvalidCardNumberException.class);
        assertThatThrownBy(() -> CardNumberValidator.requireValid("41111111111111111111")).isInstanceOf(InvalidCardNumberException.class);
        assertThatThrownBy(() -> CardNumberValidator.requireValid(null)).isInstanceOf(InvalidCardNumberException.class);
    }

    @Test
    void validationMessageDoesNotEchoInput() {
        assertThatThrownBy(() -> CardNumberValidator.requireValid("4111111111111112"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("4111"));
    }
}
