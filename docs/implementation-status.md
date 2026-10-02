# Prototype backend implementation status

This document maps the confirmed product decisions to the code that currently enforces them. It is a handoff aid, not a substitute for the automated tests referenced below.

## Product scope

| Requirement | Implementation evidence | Verification evidence |
| --- | --- | --- |
| Mumbai launch area and verified saved addresses | User Service owns saved addresses and validates pincode, coordinates, freshness and accuracy before Booking can resolve an address through an internal API. | `AddressServiceTest`; Booking matching tests assert the authoritative address snapshot is persisted. |
| Instant, scheduled and 2–7 visit multi-day bookings | `BookingWindowPolicy` enforces today/next-seven-day rules, 08:00–19:00 hours and 1–4 whole-hour duration. `BookingLifecycleService` creates every multi-day hold in one transaction. | `BookingWindowPolicyTest`; Docker-backed `SlotHoldConcurrencyTest`. |
| Atomic capacity before payment | Worker-day capacity is locked with `PESSIMISTIC_WRITE`; durable holds expire after five minutes. Network/payment calls occur outside the hold transaction. Customer booking creation is idempotent per customer/key, rejects changed request reuse and safely recovers the winning booking during concurrent retries without releasing its Plus pass. | `SlotHoldConcurrencyTest`; booking lifecycle, match-and-hold replay/race and expiry tests. |
| Automatic worker matching | Worker Service filters active, skilled, available and nearby workers before ranking favourites, ratings and distance. | `WorkerMatchingServiceTest`. |
| Full household compatibility | Matching hard-filters pets, food context, utensils, language, allergies, religious/food restrictions, accessibility, worker constraints, heavy lifting, chemicals and equipment. The accepted requirements are copied to Booking for worker disclosure and audit. | Worker matching tests; `MatchAndHoldServiceTest`; worker PostgreSQL integration test. |
| Worker tiers and pricing | Booking supplies the active catalogue base rate; Worker applies duration and one per-visit tier surcharge: Standard ₹0, Veteran ₹30 and Expert ₹50. No surge multiplier exists. | Worker matching pricing tests; catalog tests. |
| Worker schedules | Authenticated workers can list and replace only their own bounded schedule; `AVAILABLE` windows must fit 08:00–19:00. | `WorkerAvailabilityServiceTest`; worker PostgreSQL integration test. |
| Favourites and Plus continuity | User Service serializes a maximum of three favourites. Plus multi-day selection first seeks one compatible worker across every visit and prefers a common favourite. | `HouseholdServiceTest`; `MatchAndHoldServiceTest`. |
| Plus subscription, passes and reduced fees | Wallet creates a provider order from server-owned plan terms; signed capture activates the subscription. Pass holds are consumed/released/restored through idempotent events. Cancellation fees receive the configured Plus reduction. | Plus service, purchase, reconciliation and cancellation tests; User PostgreSQL integration tests. |
| Worker offer and job lifecycle | Workers receive privacy-filtered offers, accept/reject under row locks and transition assigned visits through arrival, start and completion. | Worker feed, offer and booking lifecycle tests. |
| Accurate live worker location | Worker Service stores fresh accurate points and append-only history. Booking releases locations only to the owning customer in the tracking window; arrival requires an accurate point within 200 m. | Worker location and Booking tracking tests, including the 200 m boundary. |
| Split wallet and online payment | Wallet derives the amount from Booking's stored quote, verifies ownership and hold expiry, reserves wallet funds and creates a Razorpay order only for the remainder. | Wallet service/transaction tests and Docker-backed ledger integration tests. |
| Auditable refunds | Refund amount and sources come from the captured payment, not staff or Android input. Refunds are claimed under a short lock; finance retries target only an existing record and every attempt stores the authenticated staff ID and outcome. | Finance refund unit tests and wallet PostgreSQL integration test. |
| Kafka delivery guarantees | Booking and Wallet use transactional outboxes with leases. Versioned envelopes make compatibility explicit. Consumers reject unsupported versions, claim unique event IDs in the same transaction as side effects, and use bounded retry topics plus DLT handlers. | Envelope contract, outbox publisher, consumer and concurrency tests. |
| Notifications and live updates | Notification Service stores customer and worker history, reconnectable WebSocket streams and leased push/SMS jobs. Worker-offer fan-out contains no customer identity or address. | Notification event, persistence and delivery tests. |
| Live support | Support Service owns booking-bound tickets and durable chat. Customer ownership and staff identity are authoritative; resumable SSE uses database message IDs. | Support service/transaction/security tests. |
| Mutual ratings | Each completed visit can receive one worker rating and one customer rating through role-separated APIs. | Worker and customer rating tests. |
| Role-separated authentication and edge limits | Customer, worker and four staff roles receive short-lived JWTs from separate OTP paths. Gateway authorization and identity-header replacement prevent body/header identity selection. A Redis token bucket applies distributed per-subject or direct-address limits to every routed request. | Authentication, gateway authorization integration, identity-header and rate-limit key tests. |
| Service data isolation | User, Booking, Worker, Wallet, Support and Notification each have an independent PostgreSQL configuration and migration history. Cross-service reads use internal HTTP APIs. | Compose topology, service configuration and Testcontainers suites. |

## Automated gates

- `./gradlew test bootJar --no-configuration-cache --rerun-tasks` compiles, tests and packages all seven deployable modules.
- PostgreSQL/Testcontainers suites are disabled only when Docker is unavailable locally. GitHub Actions explicitly verifies Docker and rejects any skipped, failed or errored test.
- `git diff --check` rejects whitespace errors during handoff checks.
- Repository secret scans are run before commits; credentials and the previously shared GitHub token are not stored in source.

## Inputs still required from the owner

These are intentionally not guessed because they change public identity or require third-party accounts:

1. A public product name and valid reverse-domain base package. `kotlin.snabbit` cannot be used because Kotlin reserves the `kotlin.*` namespace; source currently uses `com.snabbit.prototype`.
2. Production Razorpay credentials and webhook secret.
3. Production SMS/OTP and push providers/credentials. Checked-in local adapters mask delivery data and non-local startup fails closed when real adapters are absent.
4. Production workload identity or mTLS design to replace the prototype shared internal-service token.

The prototype remains runnable without those production credentials by using seeded identities, fake Razorpay mode and local masked notification adapters.
