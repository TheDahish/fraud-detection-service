package com.example.frauddetectionservice.infrastructure.persistence;

import com.example.frauddetectionservice.application.port.TerminalRepository;
import com.example.frauddetectionservice.domain.Terminal;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaTerminalRepository implements TerminalRepository {

    private final TerminalJpaRepository repository;

    JpaTerminalRepository(TerminalJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Terminal> findForUpdate(String terminalId) {
        return repository.findByTerminalId(terminalId).map(TerminalEntity::toDomain);
    }
}
