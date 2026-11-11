package com.example.frauddetectionservice.domain;

import java.util.regex.Pattern;

/**
 * Primary account number as a value object.
 *
 * <p>Security: the raw PAN is only reachable through {@link #unmaskedValue()}, which exists for keyed
 * tokenization. {@link #toString()} is redacted so the PAN cannot leak through string conversion, logging,
 * or exception messages built from this object.
 */
public final class CardNumber {

    private static final Pattern DIGITS = Pattern.compile("[0-9]{12,19}");

    private final String pan;

    private CardNumber(String pan) {
        this.pan = pan;
    }

    /**
     * @throws InvalidCardNumberException with a generic message. The input is never included in the exception.
     */
    public static CardNumber of(String raw) {
        if (raw == null || !DIGITS.matcher(raw).matches() || !passesLuhn(raw)) {
            throw new InvalidCardNumberException();
        }
        return new CardNumber(raw);
    }

    /**
     * Exposes the raw PAN. Use only for keyed tokenization. Never log, store, or serialize the result.
     */
    public String unmaskedValue() {
        return pan;
    }

    @Override
    public String toString() {
        return "CardNumber[REDACTED]";
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
