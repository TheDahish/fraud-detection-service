package com.example.frauddetectionservice.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/** Who may call the payment service, who may read its operational data, and how callers can trace a request. */
class AccessAndOperationsAcceptanceTest extends PaymentAcceptanceTestBase {

    @Test
    @DisplayName("A payment assessment without credentials is unauthorized")
    void requiresCredentials() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(APPLICATION_JSON)
                        .content(payment(activeTerminal(), "10.00", 0)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A valid token without the payment scope cannot assess payments")
    void requiresPaymentScope() throws Exception {
        mvc.perform(post(ENDPOINT).with(jwt().authorities(OPS_SCOPE))
                        .contentType(APPLICATION_JSON)
                        .content(payment(activeTerminal(), "10.00", 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Load balancers can probe liveness without credentials")
    void livenessIsPublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Operational metrics are readable only by operations staff, not by payment callers")
    void metricsRequireOperationsScope() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").with(jwt().authorities(ASSESS_SCOPE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").with(jwt().authorities(OPS_SCOPE)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Every assessment carries a request ID so a support ticket can be traced to logs")
    void everyResponseCarriesRequestId() throws Exception {
        submit(payment(activeTerminal(), "10.00", 0))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("A caller's own well-formed request ID is kept, so their systems can correlate it")
    void keepsCallerRequestId() throws Exception {
        mvc.perform(post(ENDPOINT).with(jwt().authorities(ASSESS_SCOPE))
                        .header("X-Request-Id", "support-ticket-123")
                        .contentType(APPLICATION_JSON)
                        .content(payment(activeTerminal(), "10.00", 0)))
                .andExpect(header().string("X-Request-Id", "support-ticket-123"));
    }

    @Test
    @DisplayName("A malformed caller request ID is replaced rather than echoed back")
    void replacesHostileRequestId() throws Exception {
        var result = mvc.perform(post(ENDPOINT).with(jwt().authorities(ASSESS_SCOPE))
                        .header("X-Request-Id", "bad<script>id")
                        .contentType(APPLICATION_JSON)
                        .content(payment(activeTerminal(), "10.00", 0)))
                .andReturn();
        String returned = result.getResponse().getHeader("X-Request-Id");
        assertThat(returned).isNotNull().doesNotContain("<").isNotEqualTo("bad<script>id");
    }
}
