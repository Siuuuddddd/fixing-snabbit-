# ADR 0001: Service boundaries and data ownership

- Status: Accepted
- Date: 2026-10-02

## Context

Booking, worker allocation and money movement need different consistency and scaling characteristics. Cross-service database access would make these boundaries unreliable and deployments tightly coupled.

## Decision

User, Booking, Worker, Wallet and Support services each own a separate PostgreSQL database. Booking is the workflow conductor. Services use synchronous HTTP for decisions needed in the current customer request and Kafka for facts already committed.

Each event-producing service writes business state and an outbox row in one local transaction. A publisher later sends the event with an aggregate key such as `bookingId` or `walletId`. Consumers store processed message IDs, making side effects idempotent. Retry and dead-letter topics are mandatory.

Redis is an optimization and coordination aid, never the source of truth for money or slot ownership.

## Consequences

- Services cannot join or query another service's tables.
- Read models may be eventually consistent.
- Cross-service workflows require explicit states, timeouts and compensating actions.
- Database-per-service is preserved in local Docker Compose as five PostgreSQL containers.
