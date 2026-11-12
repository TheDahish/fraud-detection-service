package com.example.frauddetectionservice.service;

import com.example.frauddetectionservice.config.RiskProperties;
import com.example.frauddetectionservice.entity.AssessmentEntity;
import com.example.frauddetectionservice.entity.TerminalEntity;
import com.example.frauddetectionservice.exception.UnsupportedCurrencyException;
import com.example.frauddetectionservice.model.Assessment;
import com.example.frauddetectionservice.model.Terminal;
import com.example.frauddetectionservice.repository.AssessmentRepository;
import com.example.frauddetectionservice.repository.TerminalRepository;
import com.example.frauddetectionservice.rules.AmountVsThreatScoreRule;
import com.example.frauddetectionservice.rules.RiskEngine;
import com.example.frauddetectionservice.rules.RuleCode;
import com.example.frauddetectionservice.rules.TerminalVelocityVsThreatRule;
import com.example.frauddetectionservice.rules.TerminalVelocityWindowRule;
import com.example.frauddetectionservice.rules.UnknownTerminalRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.frauddetectionservice.support.TestData.tx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FraudAssessmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private TerminalRepository terminals;
    private AssessmentRepository assessments;
    private FraudAssessmentService service;

    @BeforeEach
    void setUp() {
        terminals = mock(TerminalRepository.class);
        assessments = mock(AssessmentRepository.class);

        var properties = new RiskProperties(
                Map.of("EUR", new BigDecimal("10000")),
                new BigDecimal("5"),
                Duration.ofSeconds(60),
                10,
                10,
                70,
                new RiskProperties.Weights(0.4, 0.8, 0.4, 0.4));

        var engine = new RiskEngine(
                List.of(new UnknownTerminalRule(),
                        new AmountVsThreatScoreRule(Map.of("EUR", new BigDecimal("10000")), new BigDecimal("5")),
                        new TerminalVelocityWindowRule(10),
                        new TerminalVelocityVsThreatRule(10)),
                Map.of(RuleCode.AMOUNT_VS_THREAT_SCORE, 0.4,
                        RuleCode.UNKNOWN_TERMINAL, 0.8,
                        RuleCode.TERMINAL_VELOCITY_WINDOW, 0.4,
                        RuleCode.TERMINAL_VELOCITY_VS_THREAT, 0.4),
                70);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new FraudAssessmentService(engine, terminals, assessments, clock, properties);
    }

    @Test
    void unsupportedCurrencyIsRejectedBeforeAnyStorageAccess() {
        assertThatThrownBy(() -> service.assess(tx("10", "JPY", "T-1001", 0)))
                .isInstanceOf(UnsupportedCurrencyException.class);
        verifyNoInteractions(terminals, assessments);
    }

    @Test
    void locksTheTerminalBeforeCountingAndCountsOnlyTheWindow() {
        when(terminals.findByTerminalId("T-1001")).thenReturn(Optional.of(new TerminalEntity("T-1001", Terminal.Status.ACTIVE)));
        when(assessments.countByTerminalSince(anyString(), any())).thenReturn(3L);

        service.assess(tx("10", 0));

        InOrder order = inOrder(terminals, assessments);
        order.verify(terminals).findByTerminalId("T-1001");
        order.verify(assessments).countByTerminalSince("T-1001", NOW.minusSeconds(60));
    }

    @Test
    void recordsOneAssessmentPerCall() {
        when(terminals.findByTerminalId("T-1001")).thenReturn(Optional.of(new TerminalEntity("T-1001", Terminal.Status.ACTIVE)));
        when(assessments.countByTerminalSince(anyString(), any())).thenReturn(0L);

        service.assess(tx("10", 0));

        verify(assessments).save(any(AssessmentEntity.class));
    }

    @Test
    void unknownTerminalIsRejectedAndTheRejectionIsRecorded() {
        when(terminals.findByTerminalId("T-9999")).thenReturn(Optional.empty());
        when(assessments.countByTerminalSince(anyString(), any())).thenReturn(0L);

        Assessment result = service.assess(tx("10", "EUR", "T-9999", 0));

        assertThat(result.rejected()).isTrue();
        assertThat(result.fraudScore()).isEqualTo(80);
        verify(assessments).save(any(AssessmentEntity.class));
    }

    @Test
    void failedCountPropagatesAndNothingIsRecorded() {
        when(terminals.findByTerminalId(eq("T-1001"))).thenReturn(Optional.of(new TerminalEntity("T-1001", Terminal.Status.ACTIVE)));
        when(assessments.countByTerminalSince(anyString(), any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.assess(tx("10", 0))).isInstanceOf(IllegalStateException.class);
        verify(assessments, never()).save(any());
    }
}
