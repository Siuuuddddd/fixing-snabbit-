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

The match-and-hold route is the intended customer path. It reserves an eligible Plus pass before the booking transaction and persists gross price, pass discount and net quote separately. The preselected-worker create route remains useful for internal/prototype flows and tests. See [matching-api.md](matching-api.md) and [plus-api.md](plus-api.md).

## Customer-facing routes

- `POST /api/v1/bookings/match-and-hold` — load the authoritative household snapshot, match compatible workers, then atomically create holds and offers.
- `POST /api/v1/bookings/availability` — validate the same request without writing data and report availability, compatible-worker count and lowest price for each visit.
- `POST /api/v1/bookings/{bookingId}/payments` — start split wallet/online payment from the stored quote. Requires `Idempotency-Key`; the request never accepts an amount.
- `GET /api/v1/bookings/{bookingId}` — return the shared customer/worker duration and current lifecycle.
- `GET /api/v1/bookings/{bookingId}/visits/{visitIndex}/worker-location` — return the assigned worker's latest accurate location when the authenticated customer owns the booking and tracking is open.
- `GET /api/v1/bookings/{bookingId}/visits/{visitIndex}/worker-location/stream` — stream changed locations as `worker-location` server-sent events with keep-alive comments.
- `DELETE /api/v1/bookings/{bookingId}/hold` — cancel a pre-confirmation booking and release holds.
- `GET /api/v1/bookings/{bookingId}/cancellation-quote` — preview the current server-side fee and refund for an unstarted confirmed booking.
- `POST /api/v1/bookings/{bookingId}/cancellations` — cancel an unstarted confirmed booking and emit the authoritative partial-refund fact.

For a multi-day flow, Android should call `availability` first, highlight each unavailable visit and let the customer replace its date/time. The response also indicates whether one worker is currently compatible across every visit for Plus continuity. This is an advisory preview only: `match-and-hold` repeats matching and PostgreSQL slot locking remains the authoritative concurrency boundary.

## Internal route

- `POST /internal/v1/bookings/{bookingId}/payment-confirmation` — idempotently confirm all visits with a verified provider payment reference.
- `GET /internal/v1/bookings/{bookingId}/payment-quote` — expose the authoritative customer, state, hold expiry and quote to Wallet Service.
- `POST /internal/v1/bookings` — preselected-worker creation for trusted prototype/integration workflows; customer apps cannot bypass matching through this route.
- `POST /internal/v1/bookings/{bookingId}/platform-cancellation` — cancel a confirmed service that the platform cannot provide, grant a full refund and restore any Plus pass.

Internal routes require the shared service credential described in [authentication.md](authentication.md). They are intentionally not included in API Gateway routes and must never be called from Android based on a client-side payment result. Worker decisions are exposed separately through role-protected worker APIs documented in [worker-api.md](worker-api.md).

Worker tracking opens two hours before a confirmed visit and closes on completion or cancellation. Booking revalidates ownership and visit state throughout the stream. Points older than two minutes are withheld; coordinates are sourced from Worker Service's append-only location history rather than a client-supplied booking payload.

## Confirmed cancellation policy

Cancellation is rejected after the first visit's start time, worker arrival for customer requests, or any service activity. The configurable prototype defaults are:

- Two or more hours before the earliest visit: ₹50 standard fee.
- Less than two hours before the earliest visit: ₹100 standard fee.
- Active Plus membership reduces either fee by 50%.
- A platform cancellation before service start carries no fee and refunds the full charged quote.

The quote endpoint is advisory: the cancellation command locks and rechecks the booking and recalculates the fee, so crossing the two-hour boundary or worker arrival cannot use a stale preview. All amounts are recorded in paise on the booking. The Wallet consumer receives the refund amount from the committed Booking event; Android never supplies it.

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
