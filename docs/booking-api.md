# Booking API: milestone 2

## State flow

```text
create atomic holds
        |
        v
AWAITING_WORKER --all offers accepted--> AWAITING_PAYMENT --verified payment--> CONFIRMED
        |                                      |
        +--reject/offer timeout----------------+--hold timeout
                       |                              |
                       v                              v
             WORKER_UNAVAILABLE                    EXPIRED
```

All visits in a multi-day request use one duration and are held in one database transaction. If any capacity row is missing or any visit overlaps an active reservation, no booking, hold, offer or outbox row is committed.

The match-and-hold route is the intended customer path. The preselected-worker create route remains useful for internal/prototype flows and tests. See [matching-api.md](matching-api.md).

## Customer-facing routes

- `POST /api/v1/bookings` — create booking, holds and worker offers.
- `POST /api/v1/bookings/match-and-hold` — load the authoritative household snapshot, match compatible workers, then atomically create holds and offers.
- `POST /api/v1/bookings/{bookingId}/payments` — start split wallet/online payment from the stored quote. Requires `Idempotency-Key`; the request never accepts an amount.
- `GET /api/v1/bookings/{bookingId}` — return the shared customer/worker duration and current lifecycle.
- `POST /api/v1/bookings/{bookingId}/worker-offers/{offerId}/accept` — worker accepts one visit.
- `POST /api/v1/bookings/{bookingId}/worker-offers/{offerId}/reject` — worker rejects and releases the atomic booking.
- `DELETE /api/v1/bookings/{bookingId}/hold` — cancel a pre-confirmation booking and release holds.

## Internal route

- `POST /internal/v1/bookings/{bookingId}/payment-confirmation` — idempotently confirm all visits with a verified provider payment reference.
- `GET /internal/v1/bookings/{bookingId}/payment-quote` — expose the authoritative customer, state, hold expiry and quote to Wallet Service.

The internal route must later require service identity. It is intentionally not included in API Gateway routes and must never be called from Android based on a client-side payment result.

## Example multi-day request

```json
{
  "customerId": "40000000-0000-0000-0000-000000000001",
  "serviceId": "30000000-0000-0000-0000-000000000001",
  "bookingType": "MULTI_DAY",
  "durationHours": 2,
  "visits": [
    {
      "workerId": "10000000-0000-0000-0000-000000000001",
      "startsAt": "2026-10-03T04:30:00Z"
    },
    {
      "workerId": "10000000-0000-0000-0000-000000000001",
      "startsAt": "2026-10-04T04:30:00Z"
    }
  ]
}
```
