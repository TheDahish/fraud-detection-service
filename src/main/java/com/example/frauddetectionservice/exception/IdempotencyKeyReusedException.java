package com.example.frauddetectionservice.exception;

/** The Idempotency-Key was used on this terminal for a different payment. Message is fixed. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Idempotency-Key was already used for a different payment");
    }
}
