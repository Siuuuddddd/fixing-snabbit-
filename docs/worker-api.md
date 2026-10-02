# Worker authentication, location and job lifecycle

## Authentication

Workers authenticate independently against Worker Service:

- `POST /api/v1/worker-auth/otp/request`
- `POST /api/v1/worker-auth/otp/verify`

The OTP controls match customer authentication: Indian E.164 validation, Redis cooldown, five-minute HMAC challenge, five-attempt limit and one-time verification. The resulting 15-minute JWT contains the worker UUID as `sub` and the `WORKER` role. The gateway permits worker tokens only on `/api/v1/worker/**` and `/api/v1/workers/**`, removes spoofed identity headers and supplies `X-Authenticated-Worker-Id` from the verified token.

Prototype worker phones are `+919900000001` and `+919900000002`. OTPs are logged with masked phone numbers; API exposure remains disabled unless `OTP_EXPOSE_CODE=true`.

## Location

`POST /api/v1/workers/me/location` records a fresh location sample. It accepts latitude, longitude, `accuracyMeters` and `capturedAt`. Samples older than five minutes, more than 30 seconds in the future or less accurate than 100 metres are rejected. The current worker coordinates are updated under a row lock and every accepted sample is appended to `worker_location_history` for support and audit use.

Booking Service reads the latest durable point through the protected `GET /internal/v1/workers/{workerId}/location` API. The worker credential never reaches the customer application, and Booking applies ownership, visit-state and time-window checks before disclosing a point.

## Availability

- `GET /api/v1/workers/me/availability?from=YYYY-MM-DD&to=YYYY-MM-DD` returns the authenticated worker's schedule. The default range is today through the next seven days.
- `PUT /api/v1/workers/me/availability/{serviceDate}` creates or replaces one day. `AVAILABLE` requires `availableFrom` and `availableUntil` within 08:00–19:00; `UNAVAILABLE` needs no times.

Workers can manage today through the next 30 days. The service derives worker identity from the JWT, locks that worker while upserting the unique day row and never accepts a worker ID from the request body.

## Job flow

Authenticated worker routes are:

- `GET /api/v1/worker/bookings/offers`
- `GET /api/v1/worker/bookings/jobs`
- `POST /api/v1/worker/bookings/{bookingId}/offers/{offerId}/accept`
- `POST /api/v1/worker/bookings/{bookingId}/offers/{offerId}/reject`
- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/arrive`
- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/start`
- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/complete`

The offers feed returns at most 100 unexpired offers in expiry order. It includes the service, schedule, duration and household compatibility requirements needed for an informed decision, but never the customer ID or service coordinates. The jobs feed returns up to 100 active assigned visits in start-time order. Exact service coordinates remain absent until two hours before the visit; the booking service applies that rule rather than trusting an Android client to hide the data.

Booking also emits a transactional `WorkerOffersCreated` fact. Notification Service fans it out to the worker's registered device and live stream so a 60-second offer does not depend on aggressive polling. See [notifications-api.md](notifications-api.md) for the worker notification endpoints; accepting an offer still goes through Booking and rechecks its expiry under a row lock.

The server derives the worker ID from the JWT and checks it against the offer or visit. After payment confirmation, each visit follows:

```text
ASSIGNED -> ARRIVED -> IN_PROGRESS -> COMPLETED
```

Arrival requires a GPS sample accurate to 100 metres and within 200 metres of the booking's stored service location. Every transition uses Booking's row locks and emits an outbox event. When all visits are complete, the parent booking moves from `CONFIRMED` to `COMPLETED` and emits `BookingCompleted`.

After completion, workers and customers can rate each other through the role-protected APIs in [ratings.md](ratings.md).
