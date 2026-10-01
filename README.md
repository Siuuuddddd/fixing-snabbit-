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

This commit establishes the build, service boundaries, local infrastructure, CI and baseline outbox migrations. The next milestone implements Booking Service availability, five-minute slot holds, expiry and confirmation.

See [docs/product-decisions.md](docs/product-decisions.md) and [docs/architecture/0001-service-boundaries.md](docs/architecture/0001-service-boundaries.md).
