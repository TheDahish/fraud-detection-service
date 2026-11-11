package com.example.frauddetectionservice.infrastructure.persistence;

import com.example.frauddetectionservice.application.port.AssessmentRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit row of one assessment. The card is stored only as a keyed token; no PAN column exists.
 * Implements {@link Persistable} so Spring Data issues an INSERT for assigned IDs instead of a SELECT-then-merge.
 */
@Entity
@Table(name = "fraud_assessment")
class AssessmentEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "terminal_id", length = 32, nullable = false)
    private String terminalId;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "terminal_threat_score", nullable = false)
    private int terminalThreatScore;

    @Column(name = "card_token", length = 64, nullable = false)
    private String cardToken;

    @Column(name = "rejected", nullable = false)
    private boolean rejected;

    @Column(name = "fraud_score", nullable = false)
    private int fraudScore;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @Transient
    private boolean newEntity = true;

    protected AssessmentEntity() {
        // required by JPA
    }

    static AssessmentEntity from(AssessmentRecord record) {
        AssessmentEntity entity = new AssessmentEntity();
        entity.id = record.id();
        entity.terminalId = record.terminalId();
        entity.amount = record.amount();
        entity.currency = record.currency().getCurrencyCode();
        entity.terminalThreatScore = record.terminalThreatScore();
        entity.cardToken = record.cardToken();
        entity.rejected = record.rejected();
        entity.fraudScore = record.fraudScore();
        entity.assessedAt = record.assessedAt();
        return entity;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
