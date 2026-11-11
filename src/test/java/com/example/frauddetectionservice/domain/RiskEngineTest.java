package com.example.frauddetectionservice.domain;

import com.example.frauddetectionservice.domain.rules.AmountVsThreatScoreRule;
import com.example.frauddetectionservice.domain.rules.TerminalVelocityVsThreatRule;
import com.example.frauddetectionservice.domain.rules.TerminalVelocityWindowRule;
import com.example.frauddetectionservice.domain.rules.UnknownTerminalRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.example.frauddetectionservice.support.TestData.active;
import static com.example.frauddetectionservice.support.TestData.ctx;
import static com.example.frauddetectionservice.support.TestData.tx;
import static com.example.frauddetectionservice.support.TestData.unknownTerminal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskEngineTest {

    private static final Map<RuleCode, Double> WEIGHTS = Map.of(
            RuleCode.AMOUNT_VS_THREAT_SCORE, 0.4,
            RuleCode.UNKNOWN_TERMINAL, 0.8,
            RuleCode.TERMINAL_VELOCITY_WINDOW, 0.4,
            RuleCode.TERMINAL_VELOCITY_VS_THREAT, 0.4);

    private final RiskEngine engine = new RiskEngine(
            List.of(
                    new UnknownTerminalRule(),
                    new AmountVsThreatScoreRule(Map.of("EUR", new BigDecimal("10000")), new BigDecimal("5")),
                    new TerminalVelocityWindowRule(10),
                    new TerminalVelocityVsThreatRule(10)),
            WEIGHTS,
            70);

    @Test
    void cleanTransactionIsAllowedWithZeroScore() {
        var result = engine.assess(tx("10", 0), ctx(active("T-1001"), 0));
        assertThat(result.rejected()).isFalse();
        assertThat(result.fraudScore()).isZero();
        assertThat(result.rejectionMessage()).isNull();
        assertThat(result.triggeredRules()).isEmpty();
    }

    @Test
    void unknownTerminalAloneRejectsBecauseItsWeightedScoreIsEighty() {
        var result = engine.assess(tx("10", 0), unknownTerminal(0));
        assertThat(result.rejected()).isTrue();
        assertThat(result.fraudScore()).isEqualTo(80);
        assertThat(result.rejectionMessage()).isEqualTo("Transaction rejected: Terminal is not registered");
    }

    @Test
    void criticalRuleShortCircuitsAndIgnoresLaterRules() {
        // Blocked terminal with a huge velocity history: only the critical outcome should be reported.
        var blocked = new Terminal("T-9999", Terminal.Status.BLOCKED);
        var result = engine.assess(tx("10", 0), ctx(blocked, 500));

        assertThat(result.rejected()).isTrue();
        assertThat(result.fraudScore()).isEqualTo(100);
        assertThat(result.rejectionMessage()).isEqualTo("Terminal is blocked");
        assertThat(result.triggeredRules()).extracting(Assessment.TriggeredRule::code)
                .containsExactly(RuleCode.UNKNOWN_TERMINAL);
    }

    @Test
    void criticalAmountCapsScoreAtOneHundredWithoutWeight() {
        var result = engine.assess(tx("60000", 0), ctx(active("T-1001"), 0));
        assertThat(result.rejected()).isTrue();
        assertThat(result.fraudScore()).isEqualTo(100);
        assertThat(result.triggeredRules().get(0).weightedContribution()).isEqualTo(100.0);
    }

    @Test
    void scoredContributionsAccumulateAndCap() {
        // unknown terminal 80 + zero-headroom amount 40 = 120, capped at 100
        var result = engine.assess(tx("1", 100), unknownTerminal(0));
        assertThat(result.rejected()).isTrue();
        assertThat(result.fraudScore()).isEqualTo(100);
        assertThat(result.triggeredRules()).hasSize(2);
    }

    @Test
    void belowThresholdIsAllowedButStillReportsTheScore() {
        // threat 50 and 9000: contribution 90 x weight 0.4 = 36
        var result = engine.assess(tx("9000", 50), ctx(active("T-1001"), 0));
        assertThat(result.rejected()).isFalse();
        assertThat(result.fraudScore()).isEqualTo(36);
        assertThat(result.rejectionMessage()).isNull();
    }

    @Test
    void sumAtNinetyNineTenthsOfThresholdIsNotRoundedUpIntoRejection() {
        // weight 0.696 x contribution 100 = 69.6. Floored to 69, which stays under the threshold.
        var engine = new RiskEngine(
                List.of(new TerminalVelocityWindowRule(10)),
                Map.of(RuleCode.TERMINAL_VELOCITY_WINDOW, 0.696,
                        RuleCode.AMOUNT_VS_THREAT_SCORE, 0.0,
                        RuleCode.UNKNOWN_TERMINAL, 0.0,
                        RuleCode.TERMINAL_VELOCITY_VS_THREAT, 0.0),
                70);
        var result = engine.assess(tx("10", 0), ctx(active("T-1001"), 19));
        assertThat(result.fraudScore()).isEqualTo(69);
        assertThat(result.rejected()).isFalse();
    }

    @Test
    void exactlyAtThresholdRejects() {
        var engine = new RiskEngine(
                List.of(new TerminalVelocityWindowRule(10)),
                Map.of(RuleCode.TERMINAL_VELOCITY_WINDOW, 0.7,
                        RuleCode.AMOUNT_VS_THREAT_SCORE, 0.0,
                        RuleCode.UNKNOWN_TERMINAL, 0.0,
                        RuleCode.TERMINAL_VELOCITY_VS_THREAT, 0.0),
                70);
        var result = engine.assess(tx("10", 0), ctx(active("T-1001"), 19));
        assertThat(result.fraudScore()).isEqualTo(70);
        assertThat(result.rejected()).isTrue();
    }

    @Test
    void missingWeightFailsAtConstruction() {
        assertThatThrownBy(() -> new RiskEngine(
                List.of(new UnknownTerminalRule()),
                Map.of(),
                70)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void weightOutsideUnitIntervalFailsAtConstruction() {
        assertThatThrownBy(() -> new RiskEngine(
                List.of(new UnknownTerminalRule()),
                Map.of(RuleCode.UNKNOWN_TERMINAL, 1.5),
                70)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateRuleFailsAtConstruction() {
        assertThatThrownBy(() -> new RiskEngine(
                List.of(new UnknownTerminalRule(), new UnknownTerminalRule()),
                Map.of(RuleCode.UNKNOWN_TERMINAL, 0.8),
                70)).isInstanceOf(IllegalStateException.class);
    }
}
