package com.example.frauddetectionservice.exception;

/**
 * Thrown when a card number fails format or Luhn validation.
 * The message is fixed and does not echo the input.
 */
public class InvalidCardNumberException extends IllegalArgumentException {

    public InvalidCardNumberException() {
        super("Card number is invalid");
    }
}
