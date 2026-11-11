package com.example.frauddetectionservice.application.port;

import com.example.frauddetectionservice.domain.Terminal;

import java.util.Optional;

public interface TerminalRepository {

    /**
     * Loads the terminal and takes a row-level write lock for the rest of the surrounding transaction.
     * Serializing assessments per terminal makes the velocity count exact under concurrent requests.
     */
    Optional<Terminal> findForUpdate(String terminalId);
}
