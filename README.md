# Home Services Backend

Backend foundation for a Mumbai home-services prototype. The system supports customer and worker applications, independently deployable services, isolated PostgreSQL databases, Kafka facts, Redis caching, wallet accounting, and Razorpay integration.

> `com.snabbit.prototype` is a temporary package name. The product and package must be renamed before a public launch.

## Services

| Module | Responsibility | Local port | Database port |
| --- | --- | ---: | ---: |
| `api-gateway` | Routing and edge concerns | 8080 | — |
| `user-service` | Profiles, addresses, households and preferences | 8081 | 5433 |
| `booking-service` | Booking lifecycle, slot holds and orchestration | 8082 | 5434 |
| `worker-service` | Worker profiles, availability, tiers and matching data | 8083 | 5435 |
| `wallet-service` | Append-only ledger, reservations, top-ups and refunds | 8084 | 5436 |

No service may query another service's database. Immediate commands use HTTP APIs; durable background facts use Kafka and a transactional outbox.

## Local development

Requirements: Java 21 and Docker Desktop.

```powershell
docker compose up -d
./gradlew.bat test
./gradlew.bat :booking-service:bootRun
```

Service health is available at `/actuator/health`. Local credentials in `compose.yaml` are development-only.

## Current milestone

The Booking Service supports atomic single-day and multi-day holds, automatic household-aware worker matching, favourite-worker continuity for Plus customers, worker offer acceptance/rejection, payment-side confirmation, cancellation and scheduled expiry. Multi-day bookings are all-or-nothing: every compatible worker slot is held in one transaction or none is. Worker ranking accounts for availability, service skill, distance, household compatibility, favourites, rating and tier pricing. Every booking stores the household requirement snapshot that was disclosed before its worker offers were created.

The Wallet Service uses an append-only ledger for top-ups, reservations, split wallet/online payments, signed Razorpay webhooks and full refunds. Transactional outbox publishers deliver keyed Kafka envelopes through short database leases; consumers are idempotent and use retry/dead-letter topics. Capacity and wallet accounts are locked only for short local transactions, and network calls run after those transactions commit.

Payment confirmation is exposed only below `/internal/v1`; it is not routed by the customer API Gateway. Authentication and service-to-service authorization will be added before any real provider integration.

See [docs/booking-api.md](docs/booking-api.md), [docs/matching-api.md](docs/matching-api.md), [docs/product-decisions.md](docs/product-decisions.md) and [docs/architecture/0001-service-boundaries.md](docs/architecture/0001-service-boundaries.md).
