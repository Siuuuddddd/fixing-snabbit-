# Snabbit Plus entitlements

User Service owns Plus plans, subscriptions and discounted service passes. Wallet Service owns the Razorpay purchase order and accepts success only from a signed provider webhook. A subscription activation is idempotent by its verified purchase ID; Android cannot choose a price, duration, pass grant or activation timestamp.

## Customer API

- `GET /api/v1/users/{userId}/plus` returns active membership expiry and the number of currently available, unexpired passes.
- `POST /api/v1/users/{userId}/plus/purchases` accepts `walletId` and `planCode`, plus an `Idempotency-Key` header, and returns the Razorpay order to pay.

Both routes require a customer JWT whose subject matches `userId`. The checked-in configurable `MONTHLY` prototype plan costs ₹199 for 30 days and grants four ₹30 service passes. Android submits only `MONTHLY`; User Service supplies every monetary and entitlement term to Wallet Service.

## Trusted internal APIs

- `POST /internal/v1/users/{userId}/plus/subscriptions` activates a verified subscription and grants 0–20 passes.
- `POST /internal/v1/users/{userId}/plus/passes/reservations` reserves one compatible pass for a generated `bookingId`.
- `POST /internal/v1/users/{userId}/plus/passes/reservations/{bookingId}/consume` consumes the reservation after `BookingConfirmed`.
- `POST /internal/v1/users/{userId}/plus/passes/reservations/{bookingId}/release` compensates a failed booking creation.

Subscription activation request:

```json
{
  "sourceReference": "razorpay_payment_verified_123",
  "startsAt": "2026-10-02T04:00:00Z",
  "expiresAt": "2026-11-01T04:00:00Z",
  "passCount": 4,
  "passDiscountMinor": 3000,
  "serviceCode": null
}
```

Money values are integer paise. A null `serviceCode` creates passes usable for any service; otherwise the normalized catalog code must match. A pass discounts a booking but never reduces its payable amount below one paisa.

## Booking coordination

Booking generates the booking ID before its local transaction and asks User Service to reserve a pass until slightly after the five-minute slot hold. Booking stores `grossTotalMinor`, `plusDiscountMinor`, `quotedTotalMinor` and `plusPassId`, so Wallet always charges the durable net quote and the price breakdown remains auditable.

If atomic slot creation fails, Booking immediately calls the release endpoint. After creation, User Service consumes `BookingConfirmed` facts and releases `BookingCancelled`, `BookingExpired`, `WorkerOfferRejected` or `WorkerOfferExpired` facts with an idempotent Kafka consumer. Event occurrence time, rather than delayed consumer time, determines whether confirmation happened before pass expiry.

An expired reservation is never blindly returned to inventory: a scheduled reconciler first reads Booking's authoritative pass context outside any database transaction. It consumes passes attached to confirmed bookings, releases terminal or genuinely expired holds, and safely recovers reservations left behind if Booking crashed before creating the booking.

Confirmed customer and platform cancellations restore the consumed pass. Plus also halves the configured early or late customer cancellation fee; platform failure always remains a full-refund, zero-fee case.

The internal activation endpoint requires service authentication and must only be called by a verified subscription-payment workflow. Production should still upgrade the shared prototype credential to workload identity.

## Verified purchase flow

1. User Service resolves the requested plan from server configuration.
2. Wallet Service verifies the wallet belongs to the customer, persists an idempotent `CREATING_ORDER` purchase and then creates the Razorpay order outside that transaction.
3. A signed `payment.captured` webhook verifies the order and exact amount, marks the purchase paid and writes `PlusSubscriptionPurchased` to the transactional outbox.
4. User Service idempotently consumes that fact and activates the dated subscription and passes.

Client-side UPI or Razorpay success never activates Plus. A duplicate webhook or Kafka delivery cannot grant a second subscription or second set of passes.
