# Fraud Detection Service: Design Plan (DRAFT for review)

Status: planning only. No implementation yet.
Source brief: `Softpay - backend case - fraud detection service.pdf`

## 0. Current scaffold

- Spring Boot 4.1.2, Java 25, Maven.
- Only `spring-boot-starter` is declared. The web, validation, and actuator starters must be added.
- `src/main/...` and `src/test/...` contain only the generated application class and its context test.

## 1. Requirements summary

- One endpoint: takes an in-flight transaction and returns whether it is allowed.
- Input: amount, currency, terminal ID, terminal threat score (0-100), card number.
- Output: rejection status, rejection message, fraud score (0-100).
- At least one business rule, from the catalogue or custom.
- Database optional (mocking is fine).
- Code compiles, unit tests run, external systems may be mocked.
- Separate design document covering logging, monitoring, robustness, resilience, and framework choice.

## 2. Design iterations

### Round 1: simple layered service
Controller calls a `FraudService` containing `if` statements per rule.
- Challenge: rules cannot be tested in isolation, adding a rule edits the core service, and scoring logic is buried.
- Result: rejected.

### Round 2: Strategy rules with Chain of Responsibility
Each rule allows or rejects. The first rejection stops evaluation.
- Challenge: the output needs a 0-100 fraud score. Short-circuiting makes the score incomplete and hides which other rules fired. Rule order becomes an implicit business decision.
- Result: keep Strategy for rules, reject the chain. Evaluate all rules.

### Round 3: pure scored rules with an aggregator
Each rule returns a contribution (0-100) and a reason. An aggregator sums contributions, caps at 100, and compares the total with a configurable threshold.
- Challenge A, sum vs. max: max ignores many weak signals together. Sum can be inflated by many small signals, but the cap bounds it and every triggered rule is visible in the output. Decision: sum, capped.
- Challenge B, hard-reject rules: a second mechanism adds complexity. A rule contributing 80 already exceeds a threshold of 70 alone. Decision: one mechanism (score plus threshold).
- Challenge C, store hits: if each rule queries the store, velocity rules hit it repeatedly. Decision: load a `RiskContext` once per request. Rules become pure functions of (transaction, context). They are unit-testable without mocks. Trade-off: the context is loaded even when no rule needs a given fact, acceptable for three rules.

## 3. Architecture (ports and adapters)

```
api/          REST controller, request/response DTOs, ProblemDetail error handling
application/  FraudAssessmentService: load context -> evaluate rules -> aggregate -> record attempt -> respond
domain/       Transaction, RiskContext, RuleResult, FraudRule (interface), RuleAggregator, Decision
ports/        TerminalRegistry, TransactionHistory (outbound interfaces)
adapters/     InMemoryTerminalRegistry, InMemoryTransactionHistory (mock; Redis/JPA documented as production path)
config/       RiskProperties (@ConfigurationProperties: thresholds, limits, window), Clock bean
```

- Domain has no Spring imports.
- `Clock` is injected so time-window tests are deterministic.

## 4. Patterns

| Pattern | Where | Why |
|---|---|---|
| Strategy | `FraudRule` implementations | Add or remove rules without touching the core |
| Ports and Adapters | Terminal registry, transaction history | Mock now, Redis or JPA later, no domain change |
| Value objects (records) | Transaction, RuleResult, Decision | Immutability, validation in compact constructors |
| Aggregator | Combines rule results into score and decision | Single place for scoring policy |
| Constructor injection | All components | Testability |
| Stretch: Decorator | Timing and logging around each rule | Per-rule latency metrics without touching rules |
| Rejected | Chain of Responsibility, Template Method | See Round 2 |

## 5. Rules

Amounts are `BigDecimal` with ISO 4217 currency. Limits are configured per currency. No FX conversion (state this in the design doc).

### R1. Amount vs. threat score (stateless)
- `limit = baseLimit(currency) * (100 - threatScore) / 100`
- If `amount > limit`, contribution is graded by `amount / limit`, capped at 100.
- Threat score 100 makes the limit zero, so any amount is flagged.

### R2. Unknown terminal (registry lookup)
- Terminal ID not in the registry: fixed contribution of 80.

### R3. Terminal velocity vs. threat score (sliding window)
- `maxTxn = baseMax + (100 - threatScore) / 10` within a window (e.g. 60 seconds).
- Exceeding it gives a graded contribution.
- Threat score drives both R1 and R3.

### Stretch R4. Card velocity
- Same windowed logic keyed on a hashed card token, never the raw PAN.

### Not feasible with given input: geo-location
- The listed inputs have no location data. Document as a known gap. Add IP or geo input only if desired.

### Threshold and counting
- Reject if `fraudScore >= 70` (configurable).
- Attempts, including rejected ones, count toward velocity. Make this explicit in the design doc.

## 6. API contract

`POST /api/v1/fraud/assessments`

Request:
```json
{ "amount": 1250.00, "currency": "EUR", "terminalId": "T-1001",
  "terminalThreatScore": 35, "cardNumber": "4111111111111111" }
```

Response:
```json
{ "rejected": false, "rejectionMessage": null, "fraudScore": 22,
  "triggeredRules": [ { "code": "TERMINAL_VELOCITY", "contribution": 22, "reason": "..." } ] }
```

- Errors: RFC 9457 `ProblemDetail`.
- Validation: amount > 0, valid ISO currency, terminal ID pattern, threat score 0-100, card number 12-19 digits with Luhn check.
- Response and logs never echo the card number.
- Open question: add optional `transactionId` for idempotency and audit correlation (deviates from listed input).

## 7. Frameworks and dependencies

- Add: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`.
- Tests: `spring-boot-starter-test`, Mockito, Boot 4 MVC test slice. Confirm exact artifact names against the Boot 4.1 BOM before implementing.
- No database in the core build. Redis (windowed counters) and Postgres (audit) are documented production choices.

## 8. Test plan (about 15-20 tests)

- Each rule in isolation: boundaries, threat score 0 and 100, unknown terminal, window expiry with fixed `Clock`.
- Aggregator: cap at 100, threshold edge (69 vs. 70), reasons preserved.
- Service with mocked ports: context loaded once, attempt recorded.
- Controller slice: validation failures return 400 ProblemDetail, valid request returns the contract shape.

## 9. Design document content: high availability and security

- **PAN handling:** never logged or stored raw. Use keyed HMAC or tokenization for velocity keys. Show last four digits at most. Reduces PCI scope.
- **Availability:** stateless service, horizontally scaled. Timeout budget on the endpoint. Circuit breaker and bulkhead around the shared store, with degraded mode (stateless rules only).
- **Failure policy (decision for owner):** when the service is down or slow, fail-open protects revenue but accepts fraud risk; fail-closed protects against fraud but blocks legitimate payments. Present both with a recommendation.
- **Concurrency:** check-then-record can race under load. Production fix: atomic operation in Redis (sorted set or Lua script). Document the in-memory limitation.
- **Logging:** structured JSON, correlation ID per request, decision logged with masked card token, no PII. Audit trail separate from application logs.
- **Monitoring:** metrics per rule (trigger count, contribution), decision rate, reject rate, p95/p99 latency. Alert on reject-rate spikes and store errors.
- **Security of the endpoint:** service-to-service authentication (mTLS or OAuth2 client credentials), request size limits, rate limiting.
- **Configuration:** thresholds and limits externalized and versioned; changes audited.
- **Scaling:** stateless app; shared store for velocity state.

## 10. Open decisions for owner

1. Failure policy: fail-open or fail-closed when the service is unavailable?
2. Add `transactionId` to the request (idempotency and audit)?
3. Geo rule: add IP or location input, or document as a gap?
4. Currency handling: per-currency limits (current plan) or FX conversion?
5. Reject threshold (proposed 70) and velocity window (proposed 60 s).
6. Do rejected attempts count toward velocity (proposed yes)?
7. Score combination: sum capped at 100 (proposed) vs. max.
8. Which stretch rule, if any: card velocity?

## 11. Next steps after review

1. Apply review edits to this document.
2. Add dependencies to `pom.xml`.
3. Implement domain, then rules, then aggregator, then service, then API, then tests.
4. Write the design document from section 9, updated with final decisions.
