package com.example.frauddetectionservice.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static com.example.frauddetectionservice.support.TestData.active;
import static com.example.frauddetectionservice.support.TestData.ctx;
import static com.example.frauddetectionservice.support.TestData.tx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountVsThreatScoreRuleTest {

    // EUR base limit 10000, hard ceiling at 5x = 50000
    private final AmountVsThreatScoreRule rule =
            new AmountVsThreatScoreRule(Map.of("EUR", new BigDecimal("10000")), new BigDecimal("5"));

    private RuleOutcome evaluate(String amount, int threat) {
        return rule.evaluate(tx(amount, threat), ctx(active("T-1001"), 0));
    }

    @Test
    void amountAtTheLimitIsNotTriggered() {
        var outcome = evaluate("10000", 0);
        assertThat(outcome).isInstanceOf(RuleOutcome.Scored.class);
        assertThat(((RuleOutcome.Scored) outcome).contribution()).isZero();
    }

    @Test
    void amountJustAboveTheLimitScoresNearFifty() {
        var scored = (RuleOutcome.Scored) evaluate("10000.01", 0);
        assertThat(scored.contribution()).isEqualTo(50);
    }

    @Test
    void threatScoreShrinksTheLimit() {
        // threat 50 halves the limit to 5000, so 9000 is 1.8x the limit: 50 x 1.8 = 90
        var scored = (RuleOutcome.Scored) evaluate("9000", 50);
        assertThat(scored.contribution()).isEqualTo(90);
    }

    @Test
    void contributionIsCappedAtOneHundred() {
        // threat 50 gives limit 5000; 10000 is exactly 2x, so 50 x 2 = 100
        assertThat(((RuleOutcome.Scored) evaluate("10000", 50)).contribution()).isEqualTo(100);
        // 40000 is 8x the limit, which would be 400 before the cap
        assertThat(((RuleOutcome.Scored) evaluate("40000", 50)).contribution()).isEqualTo(100);
    }

    @Test
    void amountAtTheHardCeilingIsGradedNotCritical() {
        assertThat(evaluate("50000", 0)).isInstanceOf(RuleOutcome.Scored.class);
    }

    @Test
    void amountAboveTheHardCeilingIsCritical() {
        var outcome = evaluate("50000.01", 0);
        assertThat(outcome).isInstanceOf(RuleOutcome.Critical.class);
        assertThat(outcome.code()).isEqualTo(RuleCode.AMOUNT_VS_THREAT_SCORE);
    }

    @Test
    void threatScoreOfOneHundredLeavesNoHeadroomSoAnyAmountScoresOneHundred() {
        assertThat(((RuleOutcome.Scored) evaluate("0.01", 100)).contribution()).isEqualTo(100);
    }

    @Test
    void currencyWithoutConfiguredLimitIsAWiringError() {
        assertThatThrownBy(() -> rule.evaluate(
                tx("10", "USD", "T-1001", 0), ctx(active("T-1001"), 0)))
                .isInstanceOf(IllegalStateException.class);
    }
}
