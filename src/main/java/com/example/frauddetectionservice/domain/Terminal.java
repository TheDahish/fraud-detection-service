package com.example.frauddetectionservice.domain;

import java.util.Objects;

/** A payment terminal registered with Softpay. */
public record Terminal(String terminalId, Status status) {

    public enum Status {
        ACTIVE,
        BLOCKED
    }

    public Terminal {
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(status, "status");
    }
}
