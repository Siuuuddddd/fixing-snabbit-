# ADR 0002: Money and slot safety

- Status: Accepted
- Date: 2026-10-02

## Decision

Slot capacity is checked and locked in PostgreSQL with `SELECT FOR UPDATE` / JPA `PESSIMISTIC_WRITE`. The short transaction creates a five-minute `HELD` record and then commits. External worker or payment calls happen after the transaction.

Wallet value is represented by an append-only ledger. Available balance equals credits minus debits minus active reservations. Payment and refund webhooks are accepted only after provider signature verification and are idempotent by provider event ID.

The Android client never proves that money moved. Add Money creates a server-side Razorpay order and credits the wallet only after a verified webhook.

## Consequences

- Kafka never locks slots.
- An in-process Kotlin lock or `synchronized` block is insufficient across instances.
- Payment never begins before a real capacity hold exists.
- Mutable cached balances are not financial records.
