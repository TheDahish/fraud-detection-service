package com.example.frauddetectionservice.entity;

import com.example.frauddetectionservice.model.Terminal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "terminal")
public class TerminalEntity {

    @Id
    @Column(name = "terminal_id", length = 32)
    private String terminalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private Terminal.Status status;

    protected TerminalEntity() {
        // required by JPA
    }

    public TerminalEntity(String terminalId, Terminal.Status status) {
        this.terminalId = terminalId;
        this.status = status;
    }

    public Terminal toDomain() {
        return new Terminal(terminalId, status);
    }
}
