# Kafka delivery

## Topics and keys

| Topic | Producer | Primary consumers | Record key |
| --- | --- | --- | --- |
| `booking-events` | Booking Service | Wallet, Worker, Notification | `bookingId` |
| `wallet-events` | Wallet Service | Booking, Notification | `bookingId` for payments; `walletId` for top-ups |

Retry topics use the source topic plus `-retry` suffixes. Records exhausted after four attempts move to a `-dlt` dead-letter topic.

## Envelope

```json
{
  "schemaVersion": 1,
  "eventId": "uuid",
  "eventType": "WalletPaymentSucceeded",
  "aggregateType": "Wallet",
  "aggregateId": "booking-or-wallet-uuid",
  "occurredAt": "2026-10-02T04:00:00Z",
  "payload": {}
}
```

The Kafka key is always `aggregateId`, preserving order for one booking or wallet. Producers currently emit schema version `1`, persisted with the payload in the transactional outbox so delayed publication cannot relabel an older contract. A missing envelope version is interpreted as version `1` for rolling-upgrade compatibility; an explicitly unsupported version fails consumption and follows the normal retry/dead-letter path instead of being applied with ambiguous semantics.

## Outbox delivery

1. A business transaction writes domain state and `outbox_event` together.
2. A publisher claims a batch with PostgreSQL `FOR UPDATE SKIP LOCKED` and a 30-second lease.
3. The claim transaction commits before Kafka is contacted.
4. Kafka acknowledgment marks the row published in a new short transaction.
5. Failure releases the row with a retry time and diagnostic message.

A crash after Kafka acknowledgment but before `published_at` can produce a duplicate. This is intentional at-least-once delivery; the system prefers duplicates over lost money or booking facts.

## Consumer idempotency

Each consumer inserts `(consumer_name, eventId)` into `processed_message` in the same local transaction as its side effects. Replayed events therefore commit no second effect. Invalid messages throw, roll back the claim and flow through retry topics to the DLT.

Current cross-service reactions:

- `WalletPaymentSucceeded` confirms all Booking holds.
- `WalletPaymentFailed` cancels a pre-confirmation Booking.
- `BookingExpired` and `BookingCancelled` release a pending wallet reservation or queue a full refund for a completed payment.
- Refund provider calls occur after the Kafka-handling database transaction; a durable refund row allows scheduled retry after a crash or provider outage.
