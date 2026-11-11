package com.example.frauddetectionservice.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates rules and turns their outcomes into a decision.
 *
 * <p>Two mechanisms are combined:
 * <ul>
 *   <li><b>Critical outcomes</b> stop evaluation at once and reject with score 100. They ignore weights.</li>
 *   <li><b>Scored outcomes</b> are multiplied by their rule weight, summed, capped at 100, and compared with
 *       the reject threshold.</li>
 * </ul>
 *
 * <p>Configuration errors (missing weight, duplicate rule) fail at construction, so the service does not start
 * with an incomplete risk model.
 */
public final class RiskEngine {

    private final List<FraudRule> rules;
    private final Map<RuleCode, Double> weights;
    private final int rejectThreshold;

    public RiskEngine(Collection<FraudRule> rules, Map<RuleCode, Double> weights, int rejectThreshold) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(weights, "weights");
        if (rejectThreshold < 1 || rejectThreshold > 100) {
            throw new IllegalArgumentException("rejectThreshold must be within 1..100");
        }

        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(FraudRule::order))
                .toList();

        EnumMap<RuleCode, Double> resolved = new EnumMap<>(RuleCode.class);
        for (FraudRule rule : this.rules) {
            if (resolved.put(rule.code(), requireWeight(weights, rule.code())) != null) {
                throw new IllegalStateException("Duplicate rule registered: " + rule.code());
            }
        }
        this.weights = Map.copyOf(resolved);
        this.rejectThreshold = rejectThreshold;
    }

    public Assessment assess(Transaction transaction, RiskContext context) {
        List<RuleOutcome> evaluated = new ArrayList<>(rules.size());
        for (FraudRule rule : rules) {
            RuleOutcome outcome = rule.evaluate(transaction, context);
            evaluated.add(outcome);
            if (outcome instanceof RuleOutcome.Critical critical) {
                return criticalAssessment(evaluated, critical);
            }
        }
        return weightedAssessment(evaluated);
    }

    private Assessment criticalAssessment(List<RuleOutcome> evaluated, RuleOutcome.Critical critical) {
        List<Assessment.TriggeredRule> triggered = triggeredRules(evaluated);
        triggered.add(new Assessment.TriggeredRule(critical.code(), 100.0, critical.reason()));
        return new Assessment(true, 100, critical.reason(), triggered);
    }

    private Assessment weightedAssessment(List<RuleOutcome> evaluated) {
        List<Assessment.TriggeredRule> triggered = triggeredRules(evaluated);

        double weightedSum = triggered.stream().mapToDouble(Assessment.TriggeredRule::weightedContribution).sum();
        // Floor, not round: a weighted sum of 69.6 must not display as 70 while being allowed.
        int fraudScore = (int) Math.min(100L, Math.floor(weightedSum + 1e-9));
        boolean rejected = fraudScore >= rejectThreshold;

        String message = rejected ? rejectionMessage(triggered) : null;
        return new Assessment(rejected, fraudScore, message, triggered);
    }

    /** Triggered scored outcomes in evaluation order, each multiplied by its rule weight. */
    private List<Assessment.TriggeredRule> triggeredRules(List<RuleOutcome> evaluated) {
        List<Assessment.TriggeredRule> triggered = new ArrayList<>();
        for (RuleOutcome outcome : evaluated) {
            if (outcome instanceof RuleOutcome.Scored scored && scored.triggered()) {
                double weighted = weights.get(scored.code()) * scored.contribution();
                triggered.add(new Assessment.TriggeredRule(scored.code(), weighted, scored.reason()));
            }
        }
        return triggered;
    }

    /** The reason from the largest weighted contributor, so the message explains the main driver. */
    private static String rejectionMessage(List<Assessment.TriggeredRule> triggered) {
        return triggered.stream()
                .max(Comparator.comparingDouble(Assessment.TriggeredRule::weightedContribution))
                .map(top -> "Transaction rejected: " + top.reason())
                .orElse("Transaction rejected: risk score exceeds threshold");
    }

    private static double requireWeight(Map<RuleCode, Double> weights, RuleCode code) {
        Double weight = weights.get(code);
        if (weight == null) {
            throw new IllegalStateException("No weight configured for rule " + code);
        }
        if (weight < 0.0 || weight > 1.0) {
            throw new IllegalStateException("Weight for rule " + code + " must be within 0.0..1.0");
        }
        return weight;
    }
}
