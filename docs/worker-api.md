# Worker authentication, location and job lifecycle

## Authentication

Workers authenticate independently against Worker Service:

- `POST /api/v1/worker-auth/otp/request`
- `POST /api/v1/worker-auth/otp/verify`

The OTP controls match customer authentication: Indian E.164 validation, Redis cooldown, five-minute HMAC challenge, five-attempt limit and one-time verification. The resulting 15-minute JWT contains the worker UUID as `sub` and the `WORKER` role. The gateway permits worker tokens only on `/api/v1/worker/**` and `/api/v1/workers/**`, removes spoofed identity headers and supplies `X-Authenticated-Worker-Id` from the verified token.

Prototype worker phones are `+919900000001` and `+919900000002`. OTPs are logged with masked phone numbers; API exposure remains disabled unless `OTP_EXPOSE_CODE=true`.

## Location

`POST /api/v1/workers/me/location` records a fresh location sample. It accepts latitude, longitude, `accuracyMeters` and `capturedAt`. Samples older than five minutes, more than 30 seconds in the future or less accurate than 100 metres are rejected. The current worker coordinates are updated under a row lock and every accepted sample is appended to `worker_location_history` for support and audit use.

## Job flow

Authenticated worker routes are:

- `POST /api/v1/worker/bookings/{bookingId}/offers/{offerId}/accept`
- `POST /api/v1/worker/bookings/{bookingId}/offers/{offerId}/reject`
- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/arrive`
- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/start`
- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/complete`

The server derives the worker ID from the JWT and checks it against the offer or visit. After payment confirmation, each visit follows:

```text
ASSIGNED -> ARRIVED -> IN_PROGRESS -> COMPLETED
```

Arrival requires a GPS sample accurate to 100 metres and within 250 metres of the booking's stored service location. Every transition uses Booking's row locks and emits an outbox event. When all visits are complete, the parent booking moves from `CONFIRMED` to `COMPLETED` and emits `BookingCompleted`.
