package com.example.frauddetectionservice.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

interface TerminalJpaRepository extends JpaRepository<TerminalEntity, String> {

    /**
     * Pessimistic write lock with a bounded wait. If another assessment holds the lock longer than the timeout,
     * the request fails fast (503) instead of queueing indefinitely.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
    Optional<TerminalEntity> findByTerminalId(String terminalId);
}
