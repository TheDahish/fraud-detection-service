package com.example.frauddetectionservice.rules;

import com.example.frauddetectionservice.model.Terminal;

import java.util.Objects;
import java.util.Optional;

/**
 * Facts about the world that rules need, loaded once per request by the application layer.
 * Rules never query storage themselves, so each rule costs no I/O and evaluation is repeatable.
 *
 * @param terminal                 the registered terminal, or empty if the terminal is unknown
 * @param priorAttemptsInWindow    assessed attempts for this terminal inside the velocity window, excluding the current one
 */
public record RiskContext(Optional<Terminal> terminal, long priorAttemptsInWindow) {

    public RiskContext {
        Objects.requireNonNull(terminal, "terminal");
        if (priorAttemptsInWindow < 0) {
            throw new IllegalArgumentException("priorAttemptsInWindow must not be negative");
        }
    }

    /** Attempts including the transaction currently being assessed. */
    public long attemptsIncludingCurrent() {
        return priorAttemptsInWindow + 1;
    }
}
