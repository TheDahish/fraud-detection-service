package com.example.frauddetectionservice.rules;

import java.util.Objects;

/**
 * Result of evaluating one rule. Modelled as a sealed type rather than exceptions because a rejection
 * is an expected business outcome, not a failure: the engine decides how to react to each variant.
 */
public sealed interface RuleOutcome permits RuleOutcome.Scored, RuleOutcome.Critical {

    RuleCode code();

    String reason();

    /**
     * A graded outcome. The contribution (0..100) is multiplied by the rule's configured weight and summed
     * with the other rules. A contribution of 0 means the rule did not trigger.
     */
    record Scored(RuleCode code, int contribution, String reason) implements RuleOutcome {

        public Scored {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(reason, "reason");
            if (contribution < 0 || contribution > 100) {
                throw new IllegalArgumentException("contribution must be within 0..100");
            }
        }

        public boolean triggered() {
            return contribution > 0;
        }
    }

    /**
     * A non-negotiable outcome. Evaluation stops immediately, the transaction is rejected, and the score is 100.
     * Weights are bypassed: a critical rule is not something a weighted sum should be able to dilute.
     */
    record Critical(RuleCode code, String reason) implements RuleOutcome {

        public Critical {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
