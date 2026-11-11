package com.example.frauddetectionservice.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Card numbers are sensitive. Callers and our own logs must never reveal them. */
@ExtendWith(OutputCaptureExtension.class)
class CardDataProtectionAcceptanceTest extends PaymentAcceptanceTestBase {

    private static final String CARD = "5555555555554444";

    @Test
    @DisplayName("The card number never comes back in an approved decision")
    void approvedResponseOmitsCard() throws Exception {
        MvcResult result = submit(payment(activeTerminal(), "10.00", "EUR", 0, CARD)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(CARD);
    }

    @Test
    @DisplayName("The card number never comes back in a declined decision")
    void declinedResponseOmitsCard() throws Exception {
        MvcResult result = submit(payment(blockedTerminal(), "10.00", "EUR", 0, CARD)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(CARD);
    }

    @Test
    @DisplayName("The card number never appears in the service's logs, even for a refused request")
    void cardNeverAppearsInLogs(CapturedOutput output) throws Exception {
        submit(payment(blockedTerminal(), "10.00", "EUR", 0, CARD));
        submit(payment(activeTerminal(), "10.00", "EUR", 0, "4111111111111112"));
        submit(payment(activeTerminal(), "10.00", "EUR", 0, CARD));

        assertThat(output.getAll()).as("sanity: the capture must actually see assessment logs")
                .contains("assessment terminalId=");
        assertThat(output.getAll()).as("logs must not contain the card number").doesNotContain(CARD);
        assertThat(output.getAll()).as("logs must not contain the refused card number").doesNotContain("4111111111111112");
    }
}
