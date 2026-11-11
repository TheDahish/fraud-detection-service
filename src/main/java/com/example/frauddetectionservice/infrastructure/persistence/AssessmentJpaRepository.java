package com.example.frauddetectionservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface AssessmentJpaRepository extends JpaRepository<AssessmentEntity, UUID> {

    /** Served by the composite index on (terminal_id, assessed_at), so this is an index range scan. */
    @Query("select count(a) from AssessmentEntity a where a.terminalId = :terminalId and a.assessedAt >= :since")
    long countByTerminalSince(@Param("terminalId") String terminalId, @Param("since") Instant since);
}
