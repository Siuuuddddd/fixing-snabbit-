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
| `support-service` | Durable support tickets and live customer chat | 8085 | 5437 |
| `notification-service` | Kafka-backed history, WebSocket updates, push and SMS delivery | 8086 | 5438 |

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

Registered customers and workers authenticate with Indian-phone OTP and role-separated, short-lived JWT access tokens. The gateway validates tokens, strips spoofed identity headers and owned APIs verify the JWT subject. The Booking Service owns the active service catalog and base prices, and supports atomic single-day and multi-day holds, automatic household-aware worker matching, favourite-worker continuity for Plus customers, worker offer acceptance/rejection, payment-side confirmation, GPS-verified arrive/start/complete transitions, privacy-bounded live worker tracking, mutual post-visit ratings, cancellation and scheduled expiry. Multi-day bookings are all-or-nothing: every compatible worker slot is held in one transaction or none is. Worker ranking accounts for availability, service skill, distance, household compatibility, favourites, rating and tier pricing. Every booking stores the household requirement snapshot that was disclosed before its worker offers were created.

The Wallet Service uses an append-only ledger for top-ups, reservations, split wallet/online payments, signed Razorpay webhooks and full refunds. Booking-payment amounts come only from Booking's stored server-side quote; Wallet independently validates lifecycle, hold expiry and wallet ownership before reserving or charging money. Transactional outbox publishers deliver keyed Kafka envelopes through short database leases; consumers are idempotent and use retry/dead-letter topics. Capacity and wallet accounts are locked only for short local transactions, and network calls run after those transactions commit.

The Support Service provides booking-bound customer tickets and durable chat history. Customers may open or read only tickets for bookings they own. Server-sent event streams resume from a durable message ID and poll PostgreSQL rather than relying on instance-local memory, so reconnects and multiple service instances preserve message order.

Notification Service turns committed Booking and Wallet facts into durable customer history, reconnectable WebSocket updates and leased push/SMS delivery jobs. Critical events use both channels; delivery adapters are replaceable and the local profile logs only masked development sends.

Snabbit Plus is represented by durable subscriptions and single-use discounted passes owned by User Service. Booking reserves an eligible pass before creating its atomic hold, persists an auditable gross/discount/net price breakdown, compensates a failed hold, and consumes, releases or restores the pass from idempotent Booking event handling. Plus membership also provides priority matching, favourite-worker continuity and reduced cancellation fees. Confirmed cancellations record their fee/refund breakdown; platform inability triggers a full refund, and Wallet returns partial or full amounts to the original split-payment sources.

Payment confirmation and other trusted coordination APIs are exposed only below `/internal/v1`; they are not routed by the customer API Gateway. Every internal request also requires the environment-driven `X-Service-Token`, and all service HTTP clients attach it automatically. Production should place service ports on a private network and replace the shared prototype credential with mTLS or short-lived workload identity.

See [docs/authentication.md](docs/authentication.md), [docs/worker-api.md](docs/worker-api.md), [docs/ratings.md](docs/ratings.md), [docs/booking-api.md](docs/booking-api.md), [docs/matching-api.md](docs/matching-api.md), [docs/plus-api.md](docs/plus-api.md), [docs/support-api.md](docs/support-api.md), [docs/notifications-api.md](docs/notifications-api.md), [docs/product-decisions.md](docs/product-decisions.md) and [docs/architecture/0001-service-boundaries.md](docs/architecture/0001-service-boundaries.md).
