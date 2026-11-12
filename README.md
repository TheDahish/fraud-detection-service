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

No environment variables or external services are needed. Token verification is mocked: the service accepts two fixed bearer tokens and rejects everything else.

- `mock-token-fraud-assess` carries the `fraud:assess` scope.
- `mock-token-reports-read` carries `reports:read`, a valid token without the payment scope.

Run it either way:

```bash
docker compose up --build     # container, port 8070
./mvnw spring-boot:run        # local, port 8070
```

Health checks are public:

```bash
curl http://localhost:8070/actuator/health/liveness
curl http://localhost:8070/actuator/health/readiness
```

Logs go to a local Splunk instance through its HTTP Event Collector. The compose file uses Docker's `splunk` logging driver, so only the masked stdout lines leave the app. To use it, set `SPLUNK_HEC_TOKEN` in `.env` and have Splunk listen on `localhost:8088`. Without Splunk the service still runs, but its log lines are dropped and `docker logs` won't show them. The compose file trusts Splunk's self-signed certificate, so use it for local development only.

A local database is seeded with three terminals: `T-1001` and `T-1002` are active, and `T-9999` is blocked.

## API collection (Bruno)

[`bruno/`](bruno) holds a Bruno collection with one request per purpose: liveness, an approved payment, each rejection path, and the error responses (422, 400, 401, 403). Each request has assertions.

Start the service first (see above). Then, from the command line with the [Bruno CLI](https://docs.usebruno.com/bru-cli/overview):

```bash
cd bruno
bru run --env local
```

Or in the Bruno app: select the `local` environment and run the collection. Its tokens are the two mock tokens above.

## Test

```bash
./mvnw verify
```

- **Unit tests** cover each rule's boundaries, the engine's critical short-circuit, weighting and rounding, value objects and the log masker.
- **Acceptance tests** ([src/test/.../acceptance](src/test/java/com/example/frauddetectionservice/acceptance)) drive the HTTP API through the security layer and check the business rules: approvals, declines, input validation, access control, and card-data protection.

`./mvnw verify` runs 85 tests: 84 pass and 1 is disabled on purpose. The disabled test checks that retried requests are not counted twice, and it needs the idempotency feature, which isn't built yet.

## Security

- Callers authenticate with OAuth2 bearer tokens and need the `fraud:assess` scope for the assessment endpoint. Token verification is mocked (see Run it), so signature, issuer and audience are not checked.
- Card numbers are validated and then discarded. They are never stored, returned, or logged. Logs are masked before they're written.
- Errors never echo input, and every error is a problem+json response with a request ID.
- The container runs as a non-root user with a read-only filesystem and all Linux capabilities dropped.
- Production TLS and mutual TLS are in `application-prod.yml`. They need a keystore and truststore, which aren't in this repo.

## Known gaps

- **Token verification is mocked.** The service accepts two fixed tokens and checks no signature, issuer or audience. Real JWT validation (JWK set, issuer, audience) must replace the mock before any production use. The mock tokens are in the code, so the mock must not reach a production profile.
- **Single node.** The database is in-process H2, so each instance has its own velocity history. Horizontal scaling needs a shared PostgreSQL, as described in the design doc.
- **No idempotency.** A retried request is counted twice.
- **Caller identity isn't recorded** in the audit trail.
- **Card and geo-location rules** aren't implemented.
