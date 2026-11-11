package com.example.frauddetectionservice.config;

import com.example.frauddetectionservice.domain.FraudRule;
import com.example.frauddetectionservice.domain.RiskEngine;
import com.example.frauddetectionservice.domain.RuleCode;
import com.example.frauddetectionservice.domain.rules.AmountVsThreatScoreRule;
import com.example.frauddetectionservice.domain.rules.TerminalVelocityVsThreatRule;
import com.example.frauddetectionservice.domain.rules.TerminalVelocityWindowRule;
import com.example.frauddetectionservice.domain.rules.UnknownTerminalRule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Wires the rule catalogue into the engine. This is the only place that knows which rules are active;
 * adding a rule means implementing {@link FraudRule}, adding a weight, and listing it here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RiskProperties.class)
public class RiskConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RiskEngine riskEngine(RiskProperties properties) {
        RiskProperties.Weights weights = properties.weights();

        List<FraudRule> rules = List.of(
                new UnknownTerminalRule(),
                new AmountVsThreatScoreRule(properties.amountBaseLimits(), properties.hardCeilingMultiplier()),
                new TerminalVelocityWindowRule(properties.velocityWindowMaxAttempts()),
                new TerminalVelocityVsThreatRule(properties.velocityBaseAllowedAttempts()));

        Map<RuleCode, Double> ruleWeights = Map.of(
                RuleCode.AMOUNT_VS_THREAT_SCORE, weights.amountVsThreatScore(),
                RuleCode.UNKNOWN_TERMINAL, weights.unknownTerminal(),
                RuleCode.TERMINAL_VELOCITY_WINDOW, weights.terminalVelocityWindow(),
                RuleCode.TERMINAL_VELOCITY_VS_THREAT, weights.terminalVelocityVsThreat());

        return new RiskEngine(rules, ruleWeights, properties.rejectThreshold());
    }
}
