package com.example.frauddetectionservice.rules;

import org.junit.jupiter.api.Test;

import static com.example.frauddetectionservice.support.TestData.active;
import static com.example.frauddetectionservice.support.TestData.ctx;
import static com.example.frauddetectionservice.support.TestData.tx;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalVelocityVsThreatRuleTest {

    private final TerminalVelocityVsThreatRule rule = new TerminalVelocityVsThreatRule(10);

    private int contribution(int threat, long priorAttempts) {
        return ((RuleOutcome.Scored) rule.evaluate(tx("10", threat), ctx(active("T-1001"), priorAttempts)))
                .contribution();
    }

    @Test
    void threatZeroAllowsTheBaseCount() {
        assertThat(contribution(0, 9)).isZero();   // 10th attempt
        assertThat(contribution(0, 10)).isEqualTo(55); // 11th attempt
    }

    @Test
    void higherThreatLowersTheAllowance() {
        // threat 50 gives allowance 5, so the 6th attempt is the first excess: 50 + 50/5 = 60
        assertThat(contribution(50, 4)).isZero();
        assertThat(contribution(50, 5)).isEqualTo(60);
    }

    @Test
    void allowanceNeverDropsBelowOne() {
        // threat 100 would give 0; the floor keeps the allowance at 1, so attempt 2 is the first excess
        assertThat(contribution(100, 0)).isZero();
        assertThat(contribution(100, 1)).isEqualTo(100);
    }
}
