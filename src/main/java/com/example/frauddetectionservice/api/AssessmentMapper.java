package com.example.frauddetectionservice.api;

import com.example.frauddetectionservice.application.UnsupportedCurrencyException;
import com.example.frauddetectionservice.domain.Assessment;
import com.example.frauddetectionservice.domain.CardNumber;
import com.example.frauddetectionservice.domain.Transaction;
import com.example.frauddetectionservice.generated.model.FraudAssessmentRequest;
import com.example.frauddetectionservice.generated.model.FraudAssessmentResponse;
import com.example.frauddetectionservice.generated.model.TriggeredRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;

/** Maps between the generated contract types and the domain. Keeps generated types out of the core. */
@Component
class AssessmentMapper {

    Transaction toDomain(FraudAssessmentRequest request) {
        return new Transaction(
                request.getAmount(),
                parseCurrency(request.getCurrency()),
                request.getTerminalId(),
                request.getTerminalThreatScore(),
                CardNumber.of(request.getCardNumber()));
    }

    FraudAssessmentResponse toResponse(Assessment assessment) {
        List<TriggeredRule> triggered = assessment.triggeredRules().stream()
                .map(rule -> new TriggeredRule(
                        TriggeredRule.CodeEnum.fromValue(rule.code().name()),
                        BigDecimal.valueOf(rule.weightedContribution()).setScale(2, RoundingMode.HALF_UP),
                        rule.reason()))
                .toList();

        return new FraudAssessmentResponse(assessment.rejected(), assessment.fraudScore(), triggered)
                .rejectionMessage(assessment.rejectionMessage());
    }

    private static Currency parseCurrency(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedCurrencyException(code);
        }
    }
}
