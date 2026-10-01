# Booking event contracts

Milestone 2 writes these facts to `outbox_event` in the same PostgreSQL transaction as booking state. The Kafka publisher is a later milestone. Every Kafka record will use `bookingId` as its key and carry a unique outbox event ID in its envelope.

| Event type | Meaning | Expected consumers |
| --- | --- | --- |
| `BookingHeld` | Every requested visit was held atomically | Booking read models, analytics |
| `WorkerOffersCreated` | One offer per held visit is ready for delivery | Worker, notification |
| `WorkerOfferAccepted` | A worker accepted a visit before its deadline | Booking read models |
| `BookingAwaitingPayment` | All required offers were accepted | Payment, customer notification |
| `WorkerOfferRejected` | A worker rejected an offer and the atomic booking was released | Matching, customer notification |
| `WorkerOfferExpired` | An offer timed out and the atomic booking was released | Matching, customer notification |
| `BookingConfirmed` | Verified payment confirmed every visit | Worker, wallet, notification |
| `BookingExpired` | Capacity expired before confirmation | Payment/refund, notification |
| `BookingCancelled` | A pre-confirmation booking was cancelled | Worker, notification |

Event payloads contain identifiers, lifecycle timestamps and references needed to retrieve authoritative state. They do not contain customer addresses, phone numbers, payment credentials or provider webhook bodies.
