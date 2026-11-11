package com.example.frauddetectionservice.application.port;

import com.example.frauddetectionservice.domain.CardNumber;

/**
 * Produces a non-reversible, keyed token for a card. Tokens are stable for the same key, so they can be
 * used for velocity grouping without storing the PAN.
 */
public interface CardTokenizer {

    String tokenize(CardNumber cardNumber);
}
