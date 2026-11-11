package com.example.frauddetectionservice.application;

import com.example.frauddetectionservice.application.port.AssessmentLedger;
import com.example.frauddetectionservice.application.port.AssessmentRecord;
import com.example.frauddetectionservice.application.port.CardTokenizer;
import com.example.frauddetectionservice.application.port.TerminalRepository;
import com.example.frauddetectionservice.config.RiskProperties;
import com.example.frauddetectionservice.domain.CardNumber;
import com.example.frauddetectionservice.domain.RiskEngine;
import com.example.frauddetectionservice.domain.RuleCode;
import com.example.frauddetectionservice.domain.Assessment;
import com.example.frauddetectionservice.domain.Terminal;
import com.example.frauddetectionservice.domain.rules.AmountVsThreatScoreRule;
import com.example.frauddetectionservice.domain.rules.TerminalVelocityVsThreatRule;
import com.example.frauddetectionservice.domain.rules.TerminalVelocityWindowRule;
import com.example.frauddetectionservice.domain.rules.UnknownTerminalRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private AssessmentLedger ledger;
    private CardTokenizer tokenizer;
    private FraudAssessmentService service;

    @BeforeEach
    void setUp() {
        terminals = mock(TerminalRepository.class);
        ledger = mock(AssessmentLedger.class);
        tokenizer = mock(CardTokenizer.class);
        when(tokenizer.tokenize(any(CardNumber.class))).thenReturn("token-abc");

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
        service = new FraudAssessmentService(engine, terminals, ledger, tokenizer, clock, properties);
    }

    @Test
    void unsupportedCurrencyIsRejectedBeforeAnyStorageAccess() {
        assertThatThrownBy(() -> service.assess(tx("10", "JPY", "T-1001", 0))).isInstanceOf(UnsupportedCurrencyException.class);
        verifyNoInteractions(terminals, ledger);
    }

    @Test
    void locksTheTerminalBeforeCountingAndCountsOnlyTheWindow() {
        when(terminals.findForUpdate("T-1001")).thenReturn(Optional.of(new Terminal("T-1001", Terminal.Status.ACTIVE)));
        when(ledger.countAttemptsSince(anyString(), any())).thenReturn(3L);

        service.assess(tx("10", 0));

        InOrder order = inOrder(terminals, ledger);
        order.verify(terminals).findForUpdate("T-1001");
        order.verify(ledger).countAttemptsSince("T-1001", NOW.minusSeconds(60));
    }

    @Test
    void recordsTheTokenNotTheCardNumber() {
        when(terminals.findForUpdate("T-1001")).thenReturn(Optional.of(new Terminal("T-1001", Terminal.Status.ACTIVE)));
        when(ledger.countAttemptsSince(anyString(), any())).thenReturn(0L);

        service.assess(tx("10", 0));

        ArgumentCaptor<AssessmentRecord> captor = ArgumentCaptor.forClass(AssessmentRecord.class);
        verify(ledger).record(captor.capture());
        AssessmentRecord record = captor.getValue();
        assertThat(record.cardToken()).isEqualTo("token-abc");
        assertThat(record.assessedAt()).isEqualTo(NOW);
        assertThat(record.toString()).doesNotContain("4111111111111111");
    }

    @Test
    void recordsRejectedDecisionWithScore() {
        when(terminals.findForUpdate("T-9999")).thenReturn(Optional.empty());
        when(ledger.countAttemptsSince(anyString(), any())).thenReturn(0L);

        Assessment result = service.assess(tx("10", "EUR", "T-9999", 0));

        assertThat(result.rejected()).isTrue();
        ArgumentCaptor<AssessmentRecord> captor = ArgumentCaptor.forClass(AssessmentRecord.class);
        verify(ledger).record(captor.capture());
        assertThat(captor.getValue().rejected()).isTrue();
        assertThat(captor.getValue().fraudScore()).isEqualTo(80);
    }

    @Test
    void failedCountPropagatesAndNothingIsRecorded() {
        when(terminals.findForUpdate(eq("T-1001"))).thenReturn(Optional.of(new Terminal("T-1001", Terminal.Status.ACTIVE)));
        when(ledger.countAttemptsSince(anyString(), any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.assess(tx("10", 0))).isInstanceOf(IllegalStateException.class);
        verify(ledger, never()).record(any());
    }
}
