package com.example.frauddetectionservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.Map;

/**
 * Externalised risk model. Changing thresholds, limits, or weights is a configuration change, not a code change.
 * Values are validated at startup so an unsafe model cannot be deployed silently.
 *
 * @param amountBaseLimits        base limit per ISO 4217 code, in major units. Also defines the supported currencies.
 * @param hardCeilingMultiplier   multiple of the base limit above which a transaction is critically rejected
 * @param velocityWindow          sliding window used by the velocity rules
 * @param velocityWindowMaxAttempts  attempts allowed per window before the window rule triggers
 * @param velocityBaseAllowedAttempts  attempts allowed per window for a threat score of 0 (scaled down as threat rises)
 * @param rejectThreshold         weighted score at or above which a transaction is rejected
 * @param weights                 per-rule weights applied to graded outcomes
 */
@Validated
@ConfigurationProperties("fraud.risk")
public record RiskProperties(
        @NotEmpty Map<String, @NotNull @DecimalMin("0.01") BigDecimal> amountBaseLimits,
        @NotNull @DecimalMin("1.0") BigDecimal hardCeilingMultiplier,
        @NotNull Duration velocityWindow,
        @Min(1) int velocityWindowMaxAttempts,
        @Min(1) int velocityBaseAllowedAttempts,
        @Min(1) @Max(100) int rejectThreshold,
        @NotNull @Valid Weights weights) {

    public RiskProperties {
        if (velocityWindow != null && (velocityWindow.isNegative() || velocityWindow.isZero())) {
            throw new IllegalArgumentException("fraud.risk.velocity-window must be positive");
        }
        if (amountBaseLimits != null) {
            // Currency.getInstance rejects anything that is not a valid ISO 4217 code, failing startup on typos.
            amountBaseLimits.keySet().forEach(Currency::getInstance);
            amountBaseLimits = Map.copyOf(amountBaseLimits);
        }
    }

    public record Weights(
            @DecimalMin("0.0") @DecimalMax("1.0") double amountVsThreatScore,
            @DecimalMin("0.0") @DecimalMax("1.0") double unknownTerminal,
            @DecimalMin("0.0") @DecimalMax("1.0") double terminalVelocityWindow,
            @DecimalMin("0.0") @DecimalMax("1.0") double terminalVelocityVsThreat) {
    }
}
