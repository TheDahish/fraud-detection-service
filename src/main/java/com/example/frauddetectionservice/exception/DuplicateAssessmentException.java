package com.example.frauddetectionservice.exception;

/** The Idempotency-Key was already used on this terminal, so this request is a retry. Message is fixed. */
public class DuplicateAssessmentException extends RuntimeException {

    public DuplicateAssessmentException() {
        super("This Idempotency-Key was already used for an earlier assessment");
    }
}
