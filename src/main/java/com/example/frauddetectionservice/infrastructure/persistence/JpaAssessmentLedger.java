package com.example.frauddetectionservice.infrastructure.persistence;

import com.example.frauddetectionservice.application.port.AssessmentLedger;
import com.example.frauddetectionservice.application.port.AssessmentRecord;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
class JpaAssessmentLedger implements AssessmentLedger {

    private final AssessmentJpaRepository repository;

    JpaAssessmentLedger(AssessmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public long countAttemptsSince(String terminalId, Instant since) {
        return repository.countByTerminalSince(terminalId, since);
    }

    @Override
    public void record(AssessmentRecord record) {
        repository.save(AssessmentEntity.from(record));
    }
}
