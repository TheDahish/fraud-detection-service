# fraud-detection-service

Real-time fraud assessment for in-flight card transactions. One endpoint takes a payment and returns whether it should be declined, a fraud score from 0 to 100, and the rules that contributed.

Design decisions, trade-offs and known gaps are in [docs/DESIGN.md](docs/DESIGN.md).

## API

`POST /api/v1/fraud/assessments` (requires a bearer token with scope `fraud:assess`)

Request:

```json
{ "amount": 1250.00, "currency": "EUR", "terminalId": "T-1001",
  "terminalThreatScore": 35, "cardNumber": "4111111111111111" }
```

Response:

```json
{ "rejected": false, "fraudScore": 22, "rejectionMessage": null,
  "triggeredRules": [ { "code": "TERMINAL_VELOCITY_WINDOW", "weightedContribution": 8.80, "reason": "..." } ] }
```

The full contract is in [src/main/resources/openapi/fraud-api.yaml](src/main/resources/openapi/fraud-api.yaml). The Java interfaces and DTOs are generated from it at build time.

## Rules

| Rule | What it catches | Type |
|---|---|---|
| `UNKNOWN_TERMINAL` | Terminal not registered. Blocked terminals are rejected outright. | Scored, critical if blocked |
| `AMOUNT_VS_THREAT_SCORE` | Amount above a limit that shrinks as the terminal's threat score rises. Above 5× the base limit, rejected outright. | Scored, critical above ceiling |
| `TERMINAL_VELOCITY_WINDOW` | Too many attempts at one terminal within 60 seconds | Scored |
| `TERMINAL_VELOCITY_VS_THREAT` | Too many attempts for the terminal's threat level | Scored |

Each scored rule is multiplied by its weight, the weighted scores are summed and capped at 100, and the total is compared with the reject threshold (70). Critical outcomes reject immediately with a score of 100. All values are in `application.yml` under `fraud.risk`.

Card and geo-location rules are out of scope for this version.

## Run it

Requirements: Java 25 (or Docker).

Set the three required secrets. They're read from the environment and the service won't start without them:

```bash
export FRAUD_JWKS_URI=https://your-identity-provider/.well-known/jwks.json
export FRAUD_JWT_ISSUER=https://your-identity-provider/
export FRAUD_CARD_TOKEN_KEY=$(openssl rand -base64 32)
```

Then run it:

```bash
./mvnw spring-boot:run
```

Or with Docker. Copy `.env.example` to `.env`, fill in the values, then:

```bash
docker compose up --build
```

The service listens on port 8080. Health checks are public:

```bash
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

A local database is seeded with three terminals: `T-1001` and `T-1002` are active, and `T-9999` is blocked.

## Test

```bash
./mvnw verify
```

- **Unit tests** cover each rule's boundaries, the engine's critical short-circuit, weighting and rounding, value objects and the log masker.
- **Acceptance tests** ([src/test/.../acceptance](src/test/java/com/example/frauddetectionservice/acceptance)) drive the HTTP API through the security layer and check the business rules: approvals, declines, input validation, access control, and card-data protection.

One test is disabled on purpose: retried requests must not be counted twice. It needs the idempotency feature, which isn't built yet.

## Security

- Callers authenticate with OAuth2 bearer tokens. Tokens must be RS256, from the configured issuer, with the expected audience and the `fraud:assess` scope. Metrics need `ops:read`.
- Card numbers are never stored or logged. The database holds a keyed HMAC-SHA256 token of the card number. Logs are masked before they're written.
- Errors never echo input, and every error is a problem+json response with a request ID.
- The container runs as a non-root user with a read-only filesystem and all Linux capabilities dropped.
- Production TLS and mutual TLS are in `application-prod.yml`. They need a keystore and truststore, which aren't in this repo.

## Known gaps

- **Single node.** The database is in-process H2, so each instance has its own velocity history. Horizontal scaling needs a shared PostgreSQL, as described in the design doc.
- **No idempotency.** A retried request is counted twice.
- **Caller identity isn't recorded** in the audit trail.
- **Card and geo-location rules** aren't implemented.
