package com.example.frauddetectionservice.entity;

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
import java.util.Currency;
import java.util.UUID;

/**
 * Append-only audit row of one assessment. Holds no card data.
 * Implements {@link Persistable} so Spring Data issues an INSERT for assigned IDs instead of a SELECT-then-merge.
 */
@Entity
@Table(name = "fraud_assessment")
public class AssessmentEntity implements Persistable<UUID> {

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

    public AssessmentEntity(UUID id, String terminalId, BigDecimal amount, Currency currency,
                            int terminalThreatScore, boolean rejected, int fraudScore, Instant assessedAt) {
        this.id = id;
        this.terminalId = terminalId;
        this.amount = amount;
        this.currency = currency.getCurrencyCode();
        this.terminalThreatScore = terminalThreatScore;
        this.rejected = rejected;
        this.fraudScore = fraudScore;
        this.assessedAt = assessedAt;
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
