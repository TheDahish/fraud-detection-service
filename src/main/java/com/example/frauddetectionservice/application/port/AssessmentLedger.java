package com.example.frauddetectionservice.application.port;

import java.time.Instant;

public interface AssessmentLedger {

    /** Number of recorded assessments for the terminal at or after {@code since}. */
    long countAttemptsSince(String terminalId, Instant since);

    void record(AssessmentRecord record);
}
