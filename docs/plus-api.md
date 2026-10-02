# Snabbit Plus entitlements

User Service owns Plus subscriptions and discounted service passes. A subscription activation is idempotent by its verified payment `sourceReference`; Android cannot directly activate membership or mint passes.

## Customer API

`GET /api/v1/users/{userId}/plus` returns active membership expiry and the number of currently available, unexpired passes. The route requires a customer JWT whose subject matches `userId`.

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

The internal activation endpoint must only be called by a verified subscription-payment workflow. Service-to-service authentication remains mandatory before production exposure.
