package com.example.frauddetectionservice.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What the payments business accepts as a valid request. */
class InputRulesAcceptanceTest extends PaymentAcceptanceTestBase {

    @Test
    @DisplayName("A card number that fails the standard check digit is refused, not assessed")
    void refusesCardWithBadCheckDigit() throws Exception {
        submit(payment(activeTerminal(), "10.00", "EUR", 0, "4111111111111112"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("A card number containing letters is refused")
    void refusesCardWithLetters() throws Exception {
        submit(payment(activeTerminal(), "10.00", "EUR", 0, "4111abcd11111111"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A currency we do not handle is refused as unprocessable, not as malformed")
    void refusesCurrencyWithoutRiskLimits() throws Exception {
        submit(payment(activeTerminal(), "10", "JPY", 0, PaymentAcceptanceTestBase.VALID_CARD))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Currency codes must be uppercase ISO codes")
    void refusesLowercaseCurrency() throws Exception {
        submit(payment(activeTerminal(), "10.00", "eur", 0, PaymentAcceptanceTestBase.VALID_CARD))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Threat scores outside 0 to 100 are refused")
    void refusesThreatScoreOutOfRange() throws Exception {
        submit(payment(activeTerminal(), "10.00", "EUR", 150, PaymentAcceptanceTestBase.VALID_CARD))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Zero or negative amounts are refused")
    void refusesNonPositiveAmount() throws Exception {
        submit(payment(activeTerminal(), "0", 0)).andExpect(status().isBadRequest());
        submit(payment(activeTerminal(), "-5.00", 0)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Amounts with sub-cent precision are refused, so the stored amount is always the assessed amount")
    void refusesSubCentAmount() throws Exception {
        submit(payment(activeTerminal(), "0.001", 0)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A request carrying an unexpected field (such as a card under another name) is refused")
    void refusesUnexpectedFields() throws Exception {
        String body = """
                {"amount": 10.00, "currency": "EUR", "terminalId": "T-1001", "terminalThreatScore": 0,
                 "cardNumber": "4111111111111111", "pan": "4111111111111111"}
                """;
        submit(body).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A request missing a required field is refused")
    void refusesMissingField() throws Exception {
        submit("{\"amount\": 10.00, \"currency\": \"EUR\", \"terminalId\": \"T-1001\", \"terminalThreatScore\": 0}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A malformed request body is refused")
    void refusesMalformedJson() throws Exception {
        submit("{\"amount\": ").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An oversized request is refused before it is read")
    void refusesOversizedRequest() throws Exception {
        String padding = "x".repeat(9000);
        submit("{\"padding\": \"" + padding + "\"}").andExpect(status().isContentTooLarge());
    }
}
