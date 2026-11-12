package com.example.frauddetectionservice.exception;

/** The currency is valid ISO 4217 but no risk limits are configured for it. */
public class UnsupportedCurrencyException extends RuntimeException {

    public UnsupportedCurrencyException(String currencyCode) {
        super("Unsupported currency: " + currencyCode);
    }
}
