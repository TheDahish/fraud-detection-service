package com.example.frauddetectionservice.infrastructure.metrics;

import com.example.frauddetectionservice.domain.Assessment;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics for fraud decisions. Tags use bounded enums only, never terminal IDs or card data,
 * so cardinality stays fixed.
 */
@Component
public class AssessmentMetrics {

    private final MeterRegistry registry;

    public AssessmentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(Assessment assessment) {
        registry.counter("fraud.assessments", "decision", assessment.rejected() ? "reject" : "allow").increment();
        registry.summary("fraud.score").record(assessment.fraudScore());
        assessment.triggeredRules().forEach(rule ->
                registry.counter("fraud.rule.triggered", "rule", rule.code().name()).increment());
    }
}
