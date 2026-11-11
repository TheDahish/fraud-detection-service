package com.example.frauddetectionservice.api;

import com.example.frauddetectionservice.application.FraudAssessmentService;
import com.example.frauddetectionservice.domain.Assessment;
import com.example.frauddetectionservice.domain.Transaction;
import com.example.frauddetectionservice.generated.api.FraudAssessmentApi;
import com.example.frauddetectionservice.generated.model.FraudAssessmentRequest;
import com.example.frauddetectionservice.generated.model.FraudAssessmentResponse;
import com.example.frauddetectionservice.infrastructure.metrics.AssessmentMetrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin adapter: the HTTP contract is generated from fraud-api.yaml, so this class only maps and delegates.
 * It does not log request bodies, because they contain card numbers.
 */
@RestController
public class AssessmentController implements FraudAssessmentApi {

    private final FraudAssessmentService service;
    private final AssessmentMapper mapper;
    private final AssessmentMetrics metrics;

    public AssessmentController(FraudAssessmentService service, AssessmentMapper mapper, AssessmentMetrics metrics) {
        this.service = service;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Override
    public ResponseEntity<FraudAssessmentResponse> assessTransaction(FraudAssessmentRequest fraudAssessmentRequest) {
        Transaction transaction = mapper.toDomain(fraudAssessmentRequest);
        Assessment assessment = service.assess(transaction);
        metrics.record(assessment);
        return ResponseEntity.ok(mapper.toResponse(assessment));
    }
}
