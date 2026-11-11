package com.example.frauddetectionservice.domain;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of assessing one transaction.
 *
 * @param rejected         whether the transaction must be blocked
 * @param fraudScore       weighted and capped score in 0..100 (forced to 100 by a critical rule)
 * @param rejectionMessage fixed, non-sensitive explanation when rejected; null otherwise
 * @param triggeredRules   rules that contributed, in evaluation order
 */
public record Assessment(boolean rejected, int fraudScore, String rejectionMessage, List<TriggeredRule> triggeredRules) {

    public Assessment {
        if (fraudScore < 0 || fraudScore > 100) {
            throw new IllegalArgumentException("fraudScore must be within 0..100");
        }
        Objects.requireNonNull(triggeredRules, "triggeredRules");
        triggeredRules = List.copyOf(triggeredRules);
    }

    /**
     * @param code                  rule that contributed
     * @param weightedContribution  contribution multiplied by the rule weight (100 for critical rules)
     * @param reason                fixed explanation for the outcome
     */
    public record TriggeredRule(RuleCode code, double weightedContribution, String reason) {}
}
