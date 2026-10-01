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

## Wallet events

| Event type | Meaning | Expected consumers |
| --- | --- | --- |
| `WalletTopUpSucceeded` | A signed provider capture credited one append-only ledger row | Wallet read models, notification |
| `WalletPaymentSucceeded` | Wallet reservation and required online amount completed | Booking, notification |
| `WalletPaymentFailed` | A pending payment was cancelled or provider order creation failed | Booking, notification |
| `WalletPaymentRefundRequired` | Money arrived after capacity/payment expiry and a durable compensation refund was queued | Finance, support |
| `WalletRefundCompleted` | Wallet credit and any provider refund both completed | Booking, notification, finance |

Wallet events use `walletId` for top-up aggregates and `bookingId` for booking-payment aggregates. Raw Razorpay webhook bodies, signatures and credentials are never included.
