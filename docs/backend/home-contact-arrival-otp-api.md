# Home contact and arrival OTP API

## Checkout home contact

`PUT /api/v1/bookings/{bookingId}/home-contact` is customer-authenticated and is allowed only while a booking is `AWAITING_WORKER` or `AWAITING_PAYMENT`.

- Omitted or blank input, or `{"mode":"SELF"}`, resolves the authenticated booker's name and canonical phone from User Service. Client-supplied booker details are ignored.
- `{"mode":"OTHER","name":"Asha Rao","phone":"9876543210"}` validates the alternate contact and stores a booking-owned snapshot.
- The response contains only `name`, `maskedPhone`, and `isBooker`; full phone numbers are never returned.
- Payment creation automatically resolves SELF when no explicit choice was saved. Payment confirmation freezes the snapshot. Every service type uses the same booking-level behavior, including each visit of a multi-day booking.

Invalid fields return HTTP 422 Problem Details with `code=INVALID_HOME_CONTACT` and `fieldErrors`. Non-owners receive 403. Frozen or wrong-state updates return 409.

## Worker arrival and verification

1. `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/arrive` validates the currently assigned worker and GPS evidence. Only then does Booking Service create one per-visit challenge and dispatch Twilio Verify SMS after commit.
2. `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/verify-arrival-otp` accepts `{"code":"123456"}`. Only the assigned worker can call it. A correct, unexpired code atomically changes the challenge to `VERIFIED`, the visit to `IN_PROGRESS`, and emits one `ServiceStarted` event.
3. `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/resend-arrival-otp` is allowed only for the assigned worker while the visit is `ARRIVED`. A 30-second cooldown and three-send default apply.
4. Calling the legacy `/start` route before verification returns HTTP 409 with `code=ARRIVAL_OTP_REQUIRED` and `verificationEndpoint`.

Worker job/visit responses expose only `otpStatus` and `otpStatusDisplayText`. Customer notifications say either that the code went to the booker's phone or show only the alternate contact's last four digits. Worker notifications say whether the code was sent, failed, locked, or verified and never contain contact details.

Verification failures use Problem Details:

- `ARRIVAL_OTP_INVALID`, `ARRIVAL_OTP_EXPIRED`, or `ARRIVAL_OTP_LOCKED` (409)
- `ARRIVAL_OTP_RESEND_COOLDOWN` or `ARRIVAL_OTP_RATE_LIMITED` (409), optionally with `retryAfterSeconds`
- `ARRIVAL_OTP_SEND_LIMIT` or `ARRIVAL_OTP_NOT_AVAILABLE` (409)
- `ARRIVAL_OTP_ACCESS_DENIED` (403)
- `ARRIVAL_OTP_PROVIDER_UNAVAILABLE` (409) without provider response details

The outbox/Kafka events `ArrivalOtpSent`, `ArrivalOtpDeliveryFailed`, and `VisitOtpLocked` contain identifiers and status only. OTP digits, names, and phone numbers are forbidden.

## Lifecycle and operations

Reassignment, worker dropout, visit/booking cancellation, refund, and completion cancel or invalidate the visit challenge. Legacy confirmed bookings resolve SELF on first valid arrival. Provider delivery is retried with bounded sends; verification is bounded by database attempt count plus Redis per-visit/per-worker request limits.

There is intentionally no support override that reveals or bypasses the code. A future support override requires a separately authorized, audited endpoint and reason trail; until then support must use resend, reassignment, cancellation, or refund workflows.
