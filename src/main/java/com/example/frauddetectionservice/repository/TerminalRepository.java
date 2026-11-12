package com.example.frauddetectionservice.repository;

import com.example.frauddetectionservice.entity.TerminalEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

public interface TerminalRepository extends JpaRepository<TerminalEntity, String> {

    /**
     * Loads the terminal and takes a row-level write lock for the rest of the surrounding transaction.
     * Serializing assessments per terminal makes the velocity count exact under concurrent requests.
     * A lock held longer than the timeout fails fast (503) instead of queueing indefinitely.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
    Optional<TerminalEntity> findByTerminalId(String terminalId);
}
