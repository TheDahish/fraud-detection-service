package com.example.frauddetectionservice.infrastructure.security;

import com.example.frauddetectionservice.application.port.CardTokenizer;
import com.example.frauddetectionservice.domain.CardNumber;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Keyed tokenization with HMAC-SHA256.
 *
 * <p>Plain hashes of card numbers are not safe: the PAN space is small enough to brute-force offline. A keyed
 * HMAC means an attacker needs the key as well as the database. The key lives in the secret store, not the code.
 */
@Component
class HmacCardTokenizer implements CardTokenizer {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    HmacCardTokenizer(SecurityProperties properties) {
        byte[] raw = Base64.getDecoder().decode(properties.cardTokenKey());
        if (raw.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("fraud.security.card-token-key must decode to at least " + MIN_KEY_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(raw, ALGORITHM);
    }

    @Override
    public String tokenize(CardNumber cardNumber) {
        try {
            // Mac is not thread-safe, so a fresh instance per call keeps this stateless and safe under concurrency.
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(cardNumber.unmaskedValue().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC algorithm unavailable", e);
        }
    }
}
