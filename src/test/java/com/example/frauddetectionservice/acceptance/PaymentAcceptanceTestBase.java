package com.example.frauddetectionservice.acceptance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared setup for acceptance tests. They talk to the service only through HTTP, the way a payment
 * processor would. The one exception is onboarding a terminal, which needs the terminal registry
 * to hold it; each test creates its own terminal so tests cannot influence each other's history.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PaymentAcceptanceTestBase {

    public static final String ENDPOINT = "/api/v1/fraud/assessments";
    public static final String VALID_CARD = "4111111111111111";
    public static final SimpleGrantedAuthority ASSESS_SCOPE = new SimpleGrantedAuthority("SCOPE_fraud:assess");
    public static final SimpleGrantedAuthority OTHER_SCOPE = new SimpleGrantedAuthority("SCOPE_reports:read");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Onboards a new active terminal and returns its identifier. */
    protected String activeTerminal() {
        return onboard("ACTIVE");
    }

    protected String blockedTerminal() {
        return onboard("BLOCKED");
    }

    private String onboard(String status) {
        String id = "T-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into terminal (terminal_id, status, created_at) values (?, ?, current_timestamp)", id, status);
        return id;
    }

    protected static String payment(String terminalId, String amount, String currency, int threat, String card) {
        return """
                {"amount": %s, "currency": "%s", "terminalId": "%s", "terminalThreatScore": %d, "cardNumber": "%s"}
                """.formatted(amount, currency, terminalId, threat, card);
    }

    protected static String payment(String terminalId, String amount, int threat) {
        return payment(terminalId, amount, "EUR", threat, VALID_CARD);
    }

    /** Submits a payment with an Idempotency-Key header. */
    protected ResultActions submitWithKey(String body, String idempotencyKey) throws Exception {
        return mvc.perform(post(ENDPOINT)
                .with(jwt().authorities(ASSESS_SCOPE))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Submits a payment as a caller holding the payment scope. */
    protected ResultActions submit(String body) throws Exception {
        return mvc.perform(post(ENDPOINT)
                .with(jwt().authorities(ASSESS_SCOPE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
