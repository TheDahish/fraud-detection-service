package com.example.frauddetectionservice.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects oversized bodies before parsing, a cheap denial-of-service control.
 *
 * <p>Limitation: this checks the declared Content-Length only. Chunked requests without a length must be capped
 * at the API gateway, which is the documented production layer for body-size and rate limits.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 8 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            // Set the status directly: sendError would dispatch to /error, which the security chain denies.
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            return;
        }
        chain.doFilter(request, response);
    }
}
