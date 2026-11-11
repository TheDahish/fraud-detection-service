package com.example.frauddetectionservice.infrastructure.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Replaces the {@code %msg} conversion. Because every log line goes through this converter, masking
 * does not depend on each developer remembering to mask at the call site.
 */
public class MaskingMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogSanitizer.sanitizeMessage(event.getFormattedMessage());
    }
}
