package com.example.frauddetectionservice.acceptance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Business rules for approving and declining payments. Written from the point of view of the
 * payments business; the thresholds quoted are the agreed business limits.
 */
class PaymentDecisionAcceptanceTest extends PaymentAcceptanceTestBase {

    @Test
    @DisplayName("An ordinary payment at a registered, active terminal is approved with no risk")
    void approvesOrdinaryPayment() throws Exception {
        submit(payment(activeTerminal(), "50.00", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected").value(false))
                .andExpect(jsonPath("$.fraudScore").value(0))
                .andExpect(jsonPath("$.rejectionMessage").doesNotExist())
                .andExpect(jsonPath("$.triggeredRules").isEmpty());
    }

    @Test
    @DisplayName("A payment from a terminal we do not know is declined")
    void declinesPaymentFromUnknownTerminal() throws Exception {
        submit(payment("T-NEVER-REGISTERED", "20.00", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected").value(true))
                .andExpect(jsonPath("$.rejectionMessage").isNotEmpty());
    }

    @Test
    @DisplayName("Every payment from a blocked terminal is declined, whatever its amount")
    void declinesEveryPaymentFromBlockedTerminal() throws Exception {
        submit(payment(blockedTerminal(), "1.00", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected").value(true))
                .andExpect(jsonPath("$.fraudScore").value(100));
    }

    @Test
    @DisplayName("A payment above five times the 10,000 EUR base limit is declined outright")
    void declinesPaymentAboveHardCeiling() throws Exception {
        submit(payment(activeTerminal(), "60000.00", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected").value(true))
                .andExpect(jsonPath("$.fraudScore").value(100));
    }

    @Test
    @DisplayName("The same payment scores higher on a risky terminal than on a safe one")
    void riskierTerminalScoresSameAmountHigher() throws Exception {
        int safe = scoreOf(submit(payment(activeTerminal(), "9000.00", 0)).andReturn());
        int risky = scoreOf(submit(payment(activeTerminal(), "9000.00", 50)).andReturn());

        assertThat(safe).as("9,000 EUR is within the normal limit on a safe terminal").isZero();
        assertThat(risky).as("the lower limit on a risky terminal must raise the score").isGreaterThan(safe);
    }

    @Test
    @DisplayName("A maximum-threat terminal flags any payment against the amount rule")
    void maximumThreatTerminalFlagsAnyAmount() throws Exception {
        submit(payment(activeTerminal(), "5.00", 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggeredRules[0].code").value("AMOUNT_VS_THREAT_SCORE"))
                .andExpect(jsonPath("$.triggeredRules[0].reason").isNotEmpty());
    }

    @Test
    @DisplayName("Ten payments in a minute from a safe terminal are all approved (normal trading)")
    void normalTradingIsNotDisrupted() throws Exception {
        String terminal = activeTerminal();
        for (int i = 1; i <= 10; i++) {
            submit(payment(terminal, "25.00", 0))
                    .andExpect(jsonPath("$.rejected").value(false));
        }
    }

    @Test
    @DisplayName("A sustained burst of payments from one terminal is eventually declined")
    void sustainedBurstIsEventuallyDeclined() throws Exception {
        int firstDecline = firstDeclinedPayment(activeTerminal(), 0, 30);

        assertThat(firstDecline).as("a burst of 30 payments must hit a decline").isPositive();
        assertThat(firstDecline).as("the first 10 payments in a minute are never the problem").isGreaterThan(10);
    }

    @Test
    @DisplayName("A risky terminal gets less burst tolerance than a safe one")
    void riskyTerminalToleratesFewerBurstPaymentsThanSafeTerminal() throws Exception {
        int safeDecline = firstDeclinedPayment(activeTerminal(), 0, 30);
        int riskyDecline = firstDeclinedPayment(activeTerminal(), 50, 30);

        assertThat(safeDecline).as("safe terminal must decline within 30 payments").isPositive();
        assertThat(riskyDecline).as("risky terminal must decline within 30 payments").isPositive();
        assertThat(riskyDecline).as("risky terminal must decline sooner").isLessThan(safeDecline);
    }

    @Test
    @DisplayName("A declined payment says why; an approved payment has no rejection message")
    void declinedPaymentExplainsItself() throws Exception {
        MvcResult declined = submit(payment(blockedTerminal(), "1.00", 0)).andReturn();
        String message = JsonPath.read(declined.getResponse().getContentAsString(), "$.rejectionMessage");
        assertThat(message).isNotBlank();

        MvcResult approved = submit(payment(activeTerminal(), "1.00", 0)).andReturn();
        assertThat((Object) JsonPath.read(approved.getResponse().getContentAsString(), "$.rejectionMessage")).isNull();
    }

    @Test
    @DisplayName("A retry with the same Idempotency-Key is refused as a duplicate and is not counted again")
    void retriedPaymentIsNotCountedTwice() throws Exception {
        String terminal = activeTerminal();
        String body = payment(terminal, "25.00", 0);

        submitWithKey(body, "retry-key-001").andExpect(status().isOk());
        submitWithKey(body, "retry-key-001").andExpect(status().isConflict());

        Long recorded = jdbc.queryForObject(
                "select count(*) from fraud_assessment where terminal_id = ?", Long.class, terminal);
        assertThat(recorded).isEqualTo(1L);
    }

    @Test
    @DisplayName("An Idempotency-Key reused for a different payment is refused with 422 and recorded nothing")
    void keyReusedForADifferentPaymentIsRefused() throws Exception {
        String terminal = activeTerminal();
        submitWithKey(payment(terminal, "25.00", 0), "reuse-key-001").andExpect(status().isOk());
        submitWithKey(payment(terminal, "26.00", 0), "reuse-key-001").andExpect(status().isUnprocessableEntity());

        Long recorded = jdbc.queryForObject(
                "select count(*) from fraud_assessment where terminal_id = ?", Long.class, terminal);
        assertThat(recorded).isEqualTo(1L);
    }

    @Test
    @DisplayName("A malformed Idempotency-Key is refused with 400")
    void malformedKeyIsRefused() throws Exception {
        submitWithKey(payment(activeTerminal(), "25.00", 0), "bad key!").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Without a key, an identical repeat is assessed again and counted as a new attempt")
    void requestWithoutKeyIsNotRefused() throws Exception {
        String terminal = activeTerminal();
        String body = payment(terminal, "25.00", 0);
        submit(body).andExpect(status().isOk());
        submit(body).andExpect(status().isOk());

        Long recorded = jdbc.queryForObject(
                "select count(*) from fraud_assessment where terminal_id = ?", Long.class, terminal);
        assertThat(recorded).isEqualTo(2L);
    }

    // --- helpers -----------------------------------------------------------------------------

    private int scoreOf(MvcResult result) throws Exception {
        Integer score = JsonPath.read(result.getResponse().getContentAsString(), "$.fraudScore");
        return score;
    }

    /** Returns the 1-based index of the first declined payment in the burst, or -1 if none was declined. */
    private int firstDeclinedPayment(String terminal, int threat, int burst) throws Exception {
        for (int i = 1; i <= burst; i++) {
            String body = submit(payment(terminal, "25.00", threat)).andReturn().getResponse().getContentAsString();
            Boolean rejected = JsonPath.read(body, "$.rejected");
            if (rejected) {
                return i;
            }
        }
        return -1;
    }
}
