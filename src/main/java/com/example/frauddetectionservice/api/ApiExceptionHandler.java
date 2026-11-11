package com.example.frauddetectionservice.api;

import com.example.frauddetectionservice.application.UnsupportedCurrencyException;
import com.example.frauddetectionservice.domain.InvalidAmountException;
import com.example.frauddetectionservice.domain.InvalidCardNumberException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps failures to RFC 9457 problem details.
 *
 * <p>Security rules for error handling:
 * <ul>
 *   <li>Never echo rejected input. Messages are fixed strings and field names, not values.</li>
 *   <li>Never expose stack traces, SQL, or class names to the client.</li>
 *   <li>Log the exception class and request ID, not the message, for input and data errors.</li>
 * </ul>
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid")))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        log.debug("request validation failed fields={}", errors.stream().map(e -> e.get("field")).toList());
        return respond(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        // Parser messages can echo the offending input, so the exception message is intentionally not used.
        log.debug("unreadable request body exception={}", ex.getClass().getSimpleName());
        return respond(problem(HttpStatus.BAD_REQUEST, "Malformed request", "The request body could not be read"));
    }

    @ExceptionHandler(InvalidCardNumberException.class)
    ResponseEntity<ProblemDetail> handleInvalidCard(InvalidCardNumberException ex) {
        log.debug("card number rejected by domain validation");
        return respond(problem(HttpStatus.BAD_REQUEST, "Invalid card number", "Card number is invalid"));
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    ResponseEntity<ProblemDetail> handleUnsupportedCurrency(UnsupportedCurrencyException ex) {
        return respond(problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported currency", "The currency is not supported"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> handleDataAccess(DataAccessException ex) {
        // Fail closed: the transaction is not assessed. The caller decides how to proceed (retry or decline).
        log.error("assessment unavailable due to data access failure cause={}",
                ex.getMostSpecificCause().getClass().getName());
        return respond(problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                "Fraud assessment is temporarily unavailable"));
    }

    @ExceptionHandler(TransactionException.class)
    ResponseEntity<ProblemDetail> handleTransaction(TransactionException ex) {
        // Commit-time and boundary failures are also fail-closed 503s, not 500s.
        log.error("assessment transaction failed cause={}", ex.getMostSpecificCause().getClass().getName());
        return respond(problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                "Fraud assessment is temporarily unavailable"));
    }

    @ExceptionHandler(InvalidAmountException.class)
    ResponseEntity<ProblemDetail> handleInvalidAmount(InvalidAmountException ex) {
        return respond(problem(HttpStatus.BAD_REQUEST, "Invalid amount", "Amount has too many decimal places for the currency"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        // Framework errors (405, 415, ...) carry their own status. Anything else is a 500.
        if (ex instanceof ErrorResponse errorResponse) {
            ProblemDetail body = errorResponse.getBody();
            body.setProperty("requestId", requestId());
            return ResponseEntity.status(errorResponse.getStatusCode()).body(body);
        }
        log.error("unexpected error", ex);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred"));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        problem.setProperty("requestId", requestId());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static String requestId() {
        return Objects.requireNonNullElse(MDC.get(RequestCorrelationFilter.MDC_KEY), "unknown");
    }
}
