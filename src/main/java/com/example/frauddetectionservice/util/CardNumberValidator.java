package com.example.frauddetectionservice.util;

import com.example.frauddetectionservice.exception.InvalidCardNumberException;

import java.util.regex.Pattern;

/**
 * Format and Luhn check for a primary account number.
 *
 * <p>The number is validated and then discarded. Nothing in the service stores, logs, or returns it, so the
 * validator has no result to hold on to.
 */
public final class CardNumberValidator {

    private static final Pattern DIGITS = Pattern.compile("[0-9]{12,19}");

    private CardNumberValidator() {
    }

    /**
     * @throws InvalidCardNumberException with a fixed message that does not echo the input
     */
    public static void requireValid(String pan) {
        if (pan == null || !DIGITS.matcher(pan).matches() || !passesLuhn(pan)) {
            throw new InvalidCardNumberException();
        }
    }

    private static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleNext = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleNext) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleNext = !doubleNext;
        }
        return sum % 10 == 0;
    }
}
