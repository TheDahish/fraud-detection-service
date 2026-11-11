package com.example.frauddetectionservice.infrastructure.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void masksCardNumberInMessage() {
        assertThat(LogSanitizer.sanitizeMessage("card 4111111111111111 failed"))
                .isEqualTo("card [REDACTED-PAN] failed");
    }

    @Test
    void masksSpacedAndDashedCardNumbers() {
        assertThat(LogSanitizer.sanitizeMessage("pan 4111 1111 1111 1111 x")).doesNotContain("1111");
        assertThat(LogSanitizer.sanitizeMessage("pan 4111-1111-1111-1111 x")).doesNotContain("1111");
    }

    @Test
    void masksNumbersWithBadCheckDigitToo() {
        assertThat(LogSanitizer.sanitizeMessage("bad 4111111111111112 still masked"))
                .doesNotContain("4111111111111112");
    }

    @Test
    void masksSensitiveKeyValuePairs() {
        assertThat(LogSanitizer.sanitizeMessage("pan=4111 1111 1111 1111 ok"))
                .isEqualTo("pan=[REDACTED-PAN] ok");
        assertThat(LogSanitizer.sanitizeMessage("token=abc123 and password: hunter2"))
                .isEqualTo("token=[REDACTED] and password: [REDACTED]");
    }

    @Test
    void doesNotMaskUnrelatedKeysThatContainASensitiveWord() {
        assertThat(LogSanitizer.sanitizeMessage("span=5")).isEqualTo("span=5");
    }

    @Test
    void escapesLineBreaksInMessagesToPreventLogForging() {
        assertThat(LogSanitizer.sanitizeMessage("line1\nINFO forged"))
                .isEqualTo("line1\\nINFO forged");
    }

    @Test
    void leavesOrdinaryTextAndUuidsUnchanged() {
        String line = "amount ok 120.50 terminal T-1001 id 4d48c47b-0ae0-4a08-9158-4a89d619a05e";
        assertThat(LogSanitizer.sanitizeMessage(line)).isEqualTo(line);
    }

    @Test
    void stackTraceMasksValuesButKeepsLineBreaks() {
        String trace = "java.lang.IllegalStateException: bad pan 4111111111111111\n\tat a.B.c(B.java:1)";
        String masked = LogSanitizer.maskOnly(trace);
        assertThat(masked).contains("[REDACTED-PAN]").contains("\n\tat a.B.c(B.java:1)");
        assertThat(masked).doesNotContain("4111111111111111");
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertThat(LogSanitizer.sanitizeMessage(null)).isNull();
        assertThat(LogSanitizer.sanitizeMessage("")).isEmpty();
        assertThat(LogSanitizer.maskOnly(null)).isNull();
    }
}
