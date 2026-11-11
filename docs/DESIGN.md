# Fraud Detection Service: Design

Status: implemented, pending owner review. Companion to `DESIGN_PLAN.md`, which holds the options analysis.

## 1. Scope

- One real-time endpoint: `POST /api/v1/fraud/assessments`. Input: amount, currency, terminal ID, terminal threat score (0-100), card number. Output: rejection status, rejection message, fraud score (0-100), and the triggered rules.
- Rules implemented: amount vs. threat score, unknown terminal, terminal velocity in a window, terminal velocity vs. threat score.
- Out of scope by instruction: card usage frequency and card + geo-location. The listed inputs also contain no location data, so the geo rule cannot be computed from them.
- No unit tests were written in this iteration, as requested. Verification is described in section 10.

## 2. Architecture

Hexagonal layout: the domain has no framework imports and the adapters are replaceable.

```
api/             Controller (implements generated interface), mapper, RFC 9457 errors, request-ID and size filters
generated/       Interfaces and DTOs generated from openapi/fraud-api.yaml (contract-first)
application/     FraudAssessmentService (transaction boundary), ports, UnsupportedCurrencyException
domain/          Transaction, CardNumber, Terminal, RiskContext, RuleOutcome, RiskEngine, Assessment
domain/rules/    Four FraudRule strategies
infrastructure/  JPA adapters, HMAC tokenizer, security config, log masking, metrics
config/          Typed, validated risk properties; rule wiring; Clock
```

### Patterns

| Pattern | Where | Why |
|---|---|---|
| Strategy | `FraudRule` implementations | Rules are added or removed without changing the engine. |
| Ports and adapters | `application/port`, `infrastructure/persistence` | The domain and service do not know about JPA. |
| Sealed result type | `RuleOutcome` (Scored / Critical) | A rejection is a business outcome, not an exception. The compiler forces every outcome to be handled. |
| Contract-first | OpenAPI to generated code | The contract is the source of truth, and drift between spec and code fails the build. |
| Value object | `CardNumber`, `Transaction` | Validation happens at construction, and the card's `toString` is redacted by design. |
| Dependency injection with a fixed Clock | `RiskConfiguration` | Time-based logic is deterministic and testable. |

Rejected: Chain of Responsibility (it short-circuits and loses the score), exceptions for rejections (they hide an expected outcome behind error handling), and a shared mutable in-memory counter (it breaks as soon as there is more than one instance).

## 3. Decision model

Two mechanisms combine.

**Critical outcomes** stop evaluation immediately, reject, and set the score to 100. Weights are bypassed. Two cases are critical:
- A blocked terminal. Blocked terminals must never transact, so no weight should dilute this.
- An amount above `baseLimit × hardCeilingMultiplier` (default 5×). This is an absolute ceiling.

**Scored outcomes** each produce a contribution from 0 to 100. The engine computes `score = min(100, round(Σ weight × contribution))` and rejects when `score ≥ 70`.

Why weighted sum, capped: a max would ignore several weak signals that together indicate fraud, and the cap bounds the sum. Every triggered rule appears in the response, so the score is explainable.

Why weights are configurable: the right importance of each signal is a business decision that changes over time. Weights are validated to lie in 0.0 to 1.0 at startup.

### Rules (defaults in `application.yml`)

| Rule | Type | Logic | Default weight |
|---|---|---|---|
| `UNKNOWN_TERMINAL` | Scored (100) for unregistered; Critical for BLOCKED | An unregistered terminal contributes 100. A blocked one is rejected outright. | 0.8, so an unknown terminal alone (80) rejects |
| `AMOUNT_VS_THREAT_SCORE` | Critical above ceiling; otherwise graded | `limit = base × (100 − threat) / 100`. Above the limit, `contribution = min(100, 50 × amount / limit)`. A threat of 100 gives a zero limit and scores 100. | 0.4 |
| `TERMINAL_VELOCITY_WINDOW` | Scored | Attempts in the window (including the current one) above `maxAttempts` (10 in 60 s). `contribution = min(100, 50 + 50 × excess / max)`. | 0.4 |
| `TERMINAL_VELOCITY_VS_THREAT` | Scored | `allowed = max(1, base × (100 − threat) / 100)`, with base 10. Same graded formula. | 0.4 |

The two velocity weights are 0.4 each, so a sustained burst (about 18 attempts in a window at threat 0) rejects on velocity alone. At 0.3 each the maximum combined score was 60, below the threshold, so velocity could never reject by itself. This was found by testing and is recorded as a deliberate change.

Attempts include rejected ones. A burst of rejected attempts is itself a signal, so counting only allowed attempts would let an attacker reset the counter by repeatedly failing.

## 4. Data and persistence

- H2 in PostgreSQL mode, with Flyway migrations. Hibernate runs with `ddl-auto=validate`, so Flyway owns the schema and a mismatch fails at startup. This caught a real `CHAR` vs `VARCHAR` drift during the build.
- `terminal`: the registry. The seed data includes one blocked terminal, `T-9999`, for demonstrations.
- `fraud_assessment`: an append-only audit of each decision. It stores a keyed card token and never the PAN. The index `(terminal_id, assessed_at)` serves the velocity count as a range scan.
- The velocity query is `count(*) where terminal_id = ? and assessed_at >= ?`, bounded by a 2 s query timeout.

### Concurrency

Check-then-record on velocity has a race: two requests can both read a count of 9 and both pass. The service avoids it with a pessimistic write lock on the terminal row (`SELECT … FOR UPDATE` with a 2 s lock timeout). All assessments for one terminal are serialized inside one transaction, so the count is exact. Different terminals do not block each other.

Verified: 30 parallel requests on one terminal. The first 10 scored 0, and attempts 11–20 each produced a distinct velocity score (16.5, 18, 19.5 … 30), one per attempt. That is exactly-once counting. Attempts beyond 20 hit the cap and are indistinguishable by design.

Unknown terminals take no lock. They are rejected anyway, so there is no velocity state to protect.

Trade-off: the lock serializes throughput per terminal. A terminal is a single point-of-sale device and processes transactions sequentially in practice, so this costs little. A lock timeout returns 503 rather than queueing.

## 5. Security

### Data protection
- **PAN never persisted.** The DB has no PAN column. Velocity and audit use an HMAC-SHA256 token keyed by a secret from the environment (at least 32 bytes, validated at startup). A plain hash would be brute-forceable offline because the PAN space is small, and the key is what makes the token safe.
- **PAN never logged.** Three layers:
  1. `CardNumber.toString()` returns `CardNumber[REDACTED]`. The raw value is reachable only through `unmaskedValue()`, which is named to signal intent.
  2. Logback replaces `%msg` and `%ex` in `logback-spring.xml` with masking converters. Every line is masked, so a new logger or call site cannot bypass it.
  3. `LogSanitizer` masks 13–19 digit runs (with optional separators) and key/value pairs for card, PAN, CVV, secret, password, token and authorization keys. It also escapes CR/LF in messages to prevent log forging.
- **Errors never echo input.** Validation errors return field names and fixed messages. Malformed-body errors do not include the parser's message, since parser messages can contain the input.
- **Secrets are never printed.** `SecurityProperties.toString()` redacts the key. Secrets come from environment variables, and a missing one fails startup.

Verified: 0 card occurrences in logs across all runs. Sanitizer probe on sample inputs: PAN with spaces, PAN with a bad Luhn check digit, `cardNumber:` and `pan=` key/value forms, and a stack trace. Non-sensitive lines are unchanged.

### Authentication and authorization
- OAuth2 bearer tokens, RS256, verified against the identity provider's JWK set. Validation covers signature, expiry, issuer, and audience. The audience check stops a token minted for another service from being replayed here.
- Scope `fraud:assess` is required for the assessment endpoint. Metrics need `ops:read`, so monitoring access is separate from the payment-facing scope.
- Secure by default: `anyRequest().denyAll()`. Only the liveness and readiness probes are public.
- Stateless: no sessions, CSRF disabled. This is valid only because the API accepts bearer tokens, not cookies.

Verified: no token gives 401; wrong audience gives 401; wrong scope gives 403; metrics with the payment scope give 403; metrics with `ops:read` give 200.

### Transport and hardening
- Production profile: TLS 1.3 and 1.2 only, with mutual TLS (`client-auth: need`), so the caller must present a certificate from our trust store. This layers over the bearer token rather than replacing it.
- Plaintext HTTP is used only for the local run in this exercise.
- Request IDs: an incoming `X-Request-Id` is accepted only if it matches `[A-Za-z0-9-]{8,64}`. Anything else is replaced with a generated UUID. This prevents injected text in logs and spoofed IDs.
- Body limit: 8 KB on declared `Content-Length`, rejected with 413 before parsing. Chunked bodies are not covered and must be limited at the gateway.
- Unknown JSON fields are rejected with 400, so a PAN sent under another key is refused rather than silently ignored.
- Error responses carry no stack traces or messages (`server.error.include-*: never`).
- Security headers from Spring Security are on (`nosniff`, `X-Frame-Options`, no-store). HSTS applies over HTTPS.

## 6. Availability, resilience, failure policy

- **Application is stateless; the current database is not shared.** The app keeps no state in memory, so it can scale horizontally, but the H2 database in this build is in-process. Each instance would have its own velocity history and row locks, and a restart erases state. **Horizontal scaling requires the shared PostgreSQL that production would use.** The single-node build must not be presented as highly available.
- **Timeouts everywhere:** connection acquisition 1 s, query 2 s, row lock 2 s. A slow dependency fails fast rather than tying up threads.
- **Failure policy: fail closed.** When the database is unavailable (including lock timeouts, query timeouts and commit-time failures), the endpoint returns 503 and the transaction is not assessed. The caller decides whether to retry or decline. The alternative, fail open, protects revenue but accepts fraud risk. This is a business decision, so the service does not choose silently: the caller owns the policy and the 503 makes the outcome explicit.
- **No in-process fallback** for the rules. A rule that depends on state cannot be evaluated without that state, so guessing a result would be worse than failing.
- **Idempotency gap.** A retried request is counted twice in velocity, which can cause a false rejection after a network timeout. The brief defines no transaction ID. Recommended next step: an optional `transactionId` with a unique constraint, so retries return the original decision.

Not implemented, documented for production: a circuit breaker (Resilience4j) around the database, and a read replica or cache for the velocity count. Both add infrastructure with no benefit at this scale and no way to demonstrate them here.

## 7. Logging and monitoring

- Key=value identifiers in every line: the request ID (MDC), terminal ID, decision, score and the triggered rule codes.
- Never logged: amounts, card data, request bodies, or the raw SQL bind parameters (`org.hibernate.orm.jdbc.bind` is pinned to WARN).
- Metrics (Micrometer, exposed via actuator behind `ops:read`):
  - `fraud.assessments{decision=allow|reject}`: decision rate
  - `fraud.score`: score distribution
  - `fraud.rule.triggered{rule=…}`: which rules fire
  - HTTP latency and error rates from Boot's built-in instrumentation
- Tags use bounded enums only, so cardinality stays fixed.
- Alerting (not configured in code): reject-rate spike, 5xx or 503 rate, p99 latency, lock-timeout count.

## 8. Configuration

Thresholds, limits, windows and weights live in `application.yml`, validated at startup. Changing them is a configuration change. Production should version them and audit changes.

Required environment variables (startup fails without them):

| Variable | Purpose |
|---|---|
| `FRAUD_JWKS_URI` | Identity provider JWK set URL |
| `FRAUD_JWT_ISSUER` | Expected token issuer |
| `FRAUD_JWT_AUDIENCE` | Expected audience (defaults to `fraud-detection-service`) |
| `FRAUD_CARD_TOKEN_KEY` | Base64 HMAC key, at least 32 bytes |
| `FRAUD_TLS_*` | Keystore and truststore, production profile only |

## 9. Framework choices

- **Spring Boot 4.1.2 on Java 25**: the existing project baseline.
- **Spring Web MVC**: the service is synchronous and short-lived, and blocking JDBC fits. WebFlux would add complexity without a benefit here.
- **Spring Data JPA with Flyway**: a standard, auditable way to version the schema.
- **H2 in PostgreSQL mode**: runs in-process for the exercise. Production would use PostgreSQL with the same migrations.
- **OpenAPI generator (contract-first)**: keeps the HTTP contract in one reviewable file, and the generated interfaces enforce it at compile time.
- **Spring Security OAuth2 Resource Server**: a maintained, standard implementation rather than custom token handling.
- **Logback with a custom converter**: masking at the layout level, below the application code.

## 10. Verification performed

Done against a running instance with a locally generated RSA key and JWK set (no unit tests, as requested):

| Check | Result |
|---|---|
| Active terminal, low threat | 200, allow, score 0 |
| Unknown terminal | 200, reject, score 80 (weight alone rejects) |
| Blocked terminal | 200, reject, score 100, critical |
| Amount above limit | 200, graded contribution, weighted |
| Amount above hard ceiling | 200, reject, score 100, critical |
| Zero-headroom amount plus threat-scaled velocity | Reject at 70 as designed |
| Unknown terminal plus zero-headroom amount | Reject, score capped at 100 |
| Velocity burst, 25 parallel requests | Exactly 8 rejections, first at attempt 18, matching the arithmetic |
| 30 parallel requests on one terminal | Attempts 11–20 each produced a distinct score, so counting was exactly-once under the lock |
| Unsupported currency | 422 |
| Invalid Luhn | 400, card not echoed |
| Unknown JSON field (`pan`) | 400 |
| Malformed JSON | 400, no parser message |
| Missing token, wrong audience, wrong scope | 401, 401, 403 |
| Metrics scope | 403 with payment scope, 200 with ops scope |
| Oversized body | 413 |
| Request ID with invalid characters | Replaced with a generated ID |
| PAN in application logs (post-restart run) | 0 occurrences |
| Log sanitizer probe | All sensitive inputs masked, normal text unchanged |
| Schema validation at startup | Passes (after one real `CHAR`/`VARCHAR` fix) |

Not verified in this iteration:
- The 503 path under a real database outage (mapping is in place, not exercised).
- Production TLS and mutual TLS (profile written, no certificates provisioned).
- Chunked request bodies (intentionally gateway-enforced).

## 11. Open decisions for the owner

1. **Failure policy:** keep fail-closed (503) as the default, or move to fail-open with a stricter local limit?
2. **Idempotency:** add a `transactionId` to the request?
3. **Geo rule:** add IP or location input, or keep it documented as a gap?
4. **Currency:** per-currency limits (current) or FX conversion to a base currency?
5. **Weights and thresholds:** the current values (0.4 / 0.8 / 0.4 / 0.4, reject at 70) are defensible but untuned. They need calibration against real data.
6. **Velocity counts rejected attempts:** currently yes, by design.
7. **Stretch rule:** card-usage frequency. The `card_token` column is in place, so it needs only an index and a rule.

## 12. Review findings and disposition

An independent review was run against the code. Each finding was checked and handled as follows.

**Fixed in code**
- Commit-time transaction failures returned 500. They now map to 503 (fail closed).
- The window timestamp was captured before the lock wait, so it could be stale by up to the lock timeout. It is now captured after the lock.
- Isolation is pinned to READ_COMMITTED so the count does not depend on the database default.
- Amounts with more decimal places than the currency allows are rejected with 400. Previously the evaluated value and the stored value could disagree.
- The score is floored, not rounded. A weighted sum of 69.6 previously displayed as 70 while being allowed.
- The contract now states that critical rules report a contribution of 100 without the weight.
- Graceful shutdown is enabled.

**Accepted and documented, not fixed in this iteration**
- *Single-node persistence (High).* See section 6. Production needs shared PostgreSQL before any HA claim holds.
- *Idempotency (High).* A lost response followed by a retry double-counts velocity. The planned fix is an `Idempotency-Key` header with a unique constraint on (terminal, key).
- *Unregistered terminals take no lock (Medium).* The unknown-terminal weight (0.8) rejects alone, so the race has no effect today. It would matter if that weight were lowered below 0.7. The fix is an advisory lock keyed on the terminal ID.
- *Generated DTO `toString` prints the PAN (Medium).* The application never logs request DTOs, and the masking layer would catch a PAN in a logged string. The generator template should be customised so the DTO cannot print it.
- *401 and 403 bodies are empty, not problem+json (Medium).* Custom Spring Security entry points are needed. The 413 response is also undeclared in the contract.
- *No caller identity in the audit (Medium).* The JWT `sub` should be recorded per assessment.
- *Rejected attempts can be used to flood a terminal (Medium).* Any holder of `fraud:assess` can exhaust a terminal's velocity budget. The mitigations are a per-caller limit, or counting only allowed attempts.
- *Unbounded audit growth (Medium).* No retention job and no check that the terminal exists in the registry.
- *Chunked request bodies are unbounded (Medium).* The filter checks only the declared Content-Length. The gateway must enforce the limit.
- *TLS depends on the `prod` profile (Low).* Without it the app serves plain HTTP.
- *Log masking edge cases (Low).* PAN runs longer than 19 digits are not masked, and control characters other than CR/LF are not escaped.

**Not changed, by choice**
- The review suggested cutting some ports and the entity workaround. I kept them: they keep the domain testable, and they show the boundary design the brief asks about.
- Unit tests remain unwritten, as requested. The review's list of tests to add (rule boundaries, the per-terminal lock, problem+json bodies) is the next step once you approve the design.
