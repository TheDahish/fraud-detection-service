package com.example.frauddetectionservice.domain;

/** Amount has more decimal places than its currency's minor units allow. Message is fixed and echoes nothing. */
public class InvalidAmountException extends IllegalArgumentException {

    public InvalidAmountException() {
        super("Amount has too many decimal places for the currency");
    }
}
