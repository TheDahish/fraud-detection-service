package com.example.frauddetectionservice.infrastructure.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Replaces the {@code %ex} conversion. Exception messages can contain input values, so stack traces are masked too.
 */
public class MaskingThrowableConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogSanitizer.maskOnly(super.convert(event));
    }
}
