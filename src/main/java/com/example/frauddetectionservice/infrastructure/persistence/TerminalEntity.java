package com.example.frauddetectionservice.infrastructure.persistence;

import com.example.frauddetectionservice.domain.Terminal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "terminal")
class TerminalEntity {

    @Id
    @Column(name = "terminal_id", length = 32)
    private String terminalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private Terminal.Status status;

    protected TerminalEntity() {
        // required by JPA
    }

    Terminal toDomain() {
        return new Terminal(terminalId, status);
    }
}
