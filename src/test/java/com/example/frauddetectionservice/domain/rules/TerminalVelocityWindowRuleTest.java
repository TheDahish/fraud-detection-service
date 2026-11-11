package com.example.frauddetectionservice.domain.rules;

import com.example.frauddetectionservice.domain.RuleOutcome;
import org.junit.jupiter.api.Test;

import static com.example.frauddetectionservice.support.TestData.active;
import static com.example.frauddetectionservice.support.TestData.ctx;
import static com.example.frauddetectionservice.support.TestData.tx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminalVelocityWindowRuleTest {

    private final TerminalVelocityWindowRule rule = new TerminalVelocityWindowRule(10);

    private int contribution(long priorAttempts) {
        return ((RuleOutcome.Scored) rule.evaluate(tx("10", 0), ctx(active("T-1001"), priorAttempts)))
                .contribution();
    }

    @Test
    void tenthAttemptIsStillAllowed() {
        assertThat(contribution(9)).isZero();
    }

    @Test
    void eleventhAttemptStartsScoringAtFiftyFivePoints() {
        assertThat(contribution(10)).isEqualTo(55);
    }

    @Test
    void contributionReachesOneHundredAtTwiceTheLimit() {
        assertThat(contribution(19)).isEqualTo(100);
    }

    @Test
    void contributionIsCapped() {
        assertThat(contribution(500)).isEqualTo(100);
    }

    @Test
    void zeroMaxAttemptsIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new TerminalVelocityWindowRule(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
