package com.example.frauddetectionservice.infrastructure.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security settings. Secrets come from environment variables only; a missing value fails startup.
 *
 * @param jwt          bearer-token validation settings for the trusted identity provider
 * @param cardTokenKey base64-encoded HMAC key (at least 32 bytes) used to tokenize card numbers
 */
@Validated
@ConfigurationProperties("fraud.security")
public record SecurityProperties(@NotNull @Valid JwtSettings jwt, @NotBlank String cardTokenKey) {

    public record JwtSettings(
            @NotBlank String jwkSetUri,
            @NotBlank String issuer,
            @NotBlank String audience) {
    }

    /** Redacted so the key cannot leak if this object is ever logged or printed in a debugger. */
    @Override
    public String toString() {
        return "SecurityProperties[jwt=" + jwt + ", cardTokenKey=REDACTED]";
    }
}
