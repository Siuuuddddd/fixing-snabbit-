# Booking event contracts

Booking writes these facts to `outbox_event` in the same PostgreSQL transaction as booking state. The leased outbox publisher sends every Kafka record with `bookingId` as its key and a unique event ID plus explicit `schemaVersion` in its envelope. Consumers claim that ID in their own database transaction before applying a side effect and reject unsupported schema versions to the retry/dead-letter path.

| Event type | Meaning | Expected consumers |
| --- | --- | --- |
| `BookingHeld` | Every requested visit was held atomically | Booking read models, analytics |
| `WorkerOffersCreated` | One offer per held visit is ready for delivery | Worker, notification |
| `WorkerOfferAccepted` | A worker accepted a visit before its deadline | Booking read models |
| `BookingAwaitingPayment` | All required offers were accepted | Payment, customer notification |
| `WorkerOfferRejected` | A worker rejected an offer and the atomic booking was released | Matching, customer notification |
| `WorkerOfferExpired` | An offer timed out and the atomic booking was released | Matching, customer notification |
| `BookingConfirmed` | Verified payment confirmed every visit | Worker, wallet, notification |
| `WorkerArrived` | Assigned worker passed the service-location check | Customer tracking, support |
| `ServiceStarted` | Worker started an arrived visit | Customer tracking, support |
| `ServiceCompleted` | Worker completed one visit | Customer tracking, ratings |
| `BookingCompleted` | Every visit in the booking completed | Ratings, notification, analytics |
| `BookingExpired` | Capacity expired before confirmation | Payment/refund, notification |
| `BookingCancelled` | A pre-confirmation booking was cancelled | Worker, notification |
| `BookingCustomerCancelled` | Customer cancelled an unstarted confirmed booking with a recorded fee/refund | Wallet, worker, Plus, notification |
| `BookingPlatformCancelled` | Platform could not provide a confirmed service; full refund is required | Wallet, worker, Plus, notification, support |

User Service's Plus consumer uses `BookingConfirmed` to consume a held pass and releases it for `BookingCancelled`, `BookingExpired`, `WorkerOfferRejected` or `WorkerOfferExpired`. A customer or platform cancellation after confirmation restores the consumed pass. Delivery retries use retry topics and exhausted records move to a dead-letter topic.

Event payloads contain identifiers, lifecycle timestamps and references needed to retrieve authoritative state. They do not contain customer addresses, phone numbers, payment credentials or provider webhook bodies.

## Wallet events

| Event type | Meaning | Expected consumers |
| --- | --- | --- |
| `WalletTopUpSucceeded` | A signed provider capture credited one append-only ledger row | Wallet read models, notification |
| `WalletPaymentSucceeded` | Wallet reservation and required online amount completed | Booking, notification |
| `WalletPaymentFailed` | A pending payment was cancelled or its provider order failed validation | Booking, notification |
| `WalletPaymentRefundRequired` | Money arrived after capacity/payment expiry and a durable compensation refund was queued | Finance, support |
| `WalletRefundCompleted` | Wallet credit and any provider refund both completed | Booking, notification, finance |
| `PlusSubscriptionPurchased` | A signed capture paid an authoritative Plus plan order | User, notification, analytics |

Wallet events use `walletId` for top-up and Plus-purchase aggregates and `bookingId` for booking-payment aggregates. Raw Razorpay webhook bodies, signatures and credentials are never included.
