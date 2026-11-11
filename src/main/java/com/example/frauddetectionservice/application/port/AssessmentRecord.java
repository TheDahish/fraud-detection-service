package com.example.frauddetectionservice.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * What is persisted for each assessment. The card is stored only as a keyed token, never as a PAN.
 */
public record AssessmentRecord(
        UUID id,
        String terminalId,
        BigDecimal amount,
        Currency currency,
        int terminalThreatScore,
        String cardToken,
        boolean rejected,
        int fraudScore,
        Instant assessedAt) {
}
