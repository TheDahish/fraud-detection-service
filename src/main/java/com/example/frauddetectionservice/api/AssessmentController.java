package com.example.frauddetectionservice.api;

import com.example.frauddetectionservice.generated.api.FraudAssessmentApi;
import com.example.frauddetectionservice.generated.model.FraudAssessmentRequest;
import com.example.frauddetectionservice.generated.model.FraudAssessmentResponse;
import com.example.frauddetectionservice.model.Assessment;
import com.example.frauddetectionservice.model.Transaction;
import com.example.frauddetectionservice.service.FraudAssessmentService;
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

    public AssessmentController(FraudAssessmentService service, AssessmentMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<FraudAssessmentResponse> assessTransaction(FraudAssessmentRequest fraudAssessmentRequest) {
        Transaction transaction = mapper.toDomain(fraudAssessmentRequest);
        Assessment assessment = service.assess(transaction);
        return ResponseEntity.ok(mapper.toResponse(assessment));
    }
}
