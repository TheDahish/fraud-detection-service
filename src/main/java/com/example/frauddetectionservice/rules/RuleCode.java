package com.example.frauddetectionservice.rules;

/**
 * Stable identifiers for every risk rule. Names are part of the public contract (see fraud-api.yaml),
 * so renaming one is a breaking change.
 */
public enum RuleCode {
    AMOUNT_VS_THREAT_SCORE,
    UNKNOWN_TERMINAL,
    TERMINAL_VELOCITY_WINDOW,
    TERMINAL_VELOCITY_VS_THREAT
}
