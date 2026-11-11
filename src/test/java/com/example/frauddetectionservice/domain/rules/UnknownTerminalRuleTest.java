package com.example.frauddetectionservice.domain.rules;

import com.example.frauddetectionservice.domain.RuleOutcome;
import com.example.frauddetectionservice.domain.Terminal;
import org.junit.jupiter.api.Test;

import static com.example.frauddetectionservice.support.TestData.ctx;
import static com.example.frauddetectionservice.support.TestData.tx;
import static com.example.frauddetectionservice.support.TestData.unknownTerminal;
import static org.assertj.core.api.Assertions.assertThat;

class UnknownTerminalRuleTest {

    private final UnknownTerminalRule rule = new UnknownTerminalRule();

    @Test
    void unregisteredTerminalScoresOneHundred() {
        var outcome = rule.evaluate(tx("10", 0), unknownTerminal(0));
        assertThat(outcome).isInstanceOf(RuleOutcome.Scored.class);
        assertThat(((RuleOutcome.Scored) outcome).contribution()).isEqualTo(100);
    }

    @Test
    void activeTerminalDoesNotTrigger() {
        var outcome = rule.evaluate(tx("10", 0), ctx(new Terminal("T-1001", Terminal.Status.ACTIVE), 0));
        assertThat(((RuleOutcome.Scored) outcome).contribution()).isZero();
    }

    @Test
    void blockedTerminalIsCritical() {
        var outcome = rule.evaluate(tx("10", 0), ctx(new Terminal("T-9999", Terminal.Status.BLOCKED), 0));
        assertThat(outcome).isInstanceOf(RuleOutcome.Critical.class);
    }
}
