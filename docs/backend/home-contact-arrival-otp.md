# Home contact and arrival OTP audit

## Status

Step 0 audit completed against `04bf345` on `feature/backend-foundation`. No feature code exists yet. The implementation must stay backend-only and reuse the booking lifecycle, internal service authentication, transactional outbox, notification delivery, and Twilio Verify foundations described below.

The shared `platform-common` `RestClientConfiguration` already supplies a prototype-scoped `RestClient.Builder` behind `@ConditionalOnMissingBean`. `NotificationApplicationSmokeTest` and `SupportApplicationSmokeTest` both load their full Spring contexts and assert that bean exists, so the previously reported startup blocker is already fixed.

## Booking and visit persistence

### `booking`

`Booking` maps the `booking` table and owns the plan-level lifecycle. Its primary key is `id`. It stores the customer, service, booking type, status, legacy plan duration, total quote, service location/address snapshot, household requirement snapshot, payment reference, Plus/pass and expert/fallback choices, hold lifetime, cancellation/refund data, timestamps, and optimistic-lock version.

Current statuses are `AWAITING_WORKER`, `AWAITING_PAYMENT`, `CONFIRMED`, `WORKER_UNAVAILABLE`, `EXPIRED`, `CANCELLED`, and `COMPLETED`. A newly matched plan starts in `AWAITING_WORKER`; acceptance of every required worker offer moves it to `AWAITING_PAYMENT`; the wallet success event confirms it; terminal expiry/cancellation/unavailability/completion states remain plan-level.

The home-contact snapshot belongs here. The additive migration must add `home_contact_name`, `home_contact_phone_e164`, `home_contact_is_booker`, `home_contact_updated_at`, and `home_contact_frozen_at`. Existing rows remain nullable and are treated as unresolved SELF only when a later operation needs the contact.

### `slot_hold` and visits

There is no separate `visit` table. A persisted visit is a `slot_hold` row. It has its own UUID `id` and is also uniquely keyed by `(booking_id, visit_index)` through `uq_slot_hold_booking_visit`; `visit_index` is currently constrained to 0–29. The row owns its assigned worker, time range, hold status/expiry, job status, arrival evidence, per-visit quote/assignment/refund/requirements snapshot, and optimistic-lock version.

`V16__per_visit_duration.sql` added non-null `slot_hold.duration_minutes`, backfilled it from the legacy booking duration, and constrained each visit to 60, 120, 180, or 240 minutes. `starts_at`, `ends_at`, and `duration_minutes` are authoritative per visit. API snapshots expose `visitIndex`, local date/start time, legacy instant fields, duration, quote, assignment, job state, and refund state.

Job states are `ASSIGNED`, `ARRIVED`, `IN_PROGRESS`, and `COMPLETED`. Hold states remain separate (`HELD`, `CONFIRMED`, `EXPIRED`, or `RELEASED`). The arrival challenge should reference the stable visit identity, preferably `slot_hold_id`, while retaining `booking_id` and `visit_index` for authorization and API lookup.

## Checkout and payment path

The customer calls `POST /api/v1/bookings/{bookingId}/payments` with `walletId`, `useWallet`, and an `Idempotency-Key`. `BookingPaymentController` takes the authenticated customer ID from the gateway-injected header and passes it to `BookingPaymentService`.

`BookingPaymentService` reads the authoritative quote from `BookingLifecycleService`, checks ownership, requires `AWAITING_PAYMENT`, a live hold, and a positive INR quote, then extends the holds within the configured maximum lifetime. It delegates to `BookingPaymentClient`, which performs an internally authenticated call to Wallet Service at `POST /internal/v1/wallets/{walletId}/booking-payments`. Wallet Service owns the booking payment record, wallet reservation/split, and provider integration; it creates the Razorpay order when an online portion is required and returns the provider order ID. Booking Service does not create the Razorpay order.

Client/provider callbacks are not confirmation. Wallet Service verifies the signed Razorpay webhook and publishes `WalletPaymentSucceeded`. Booking Service consumes that durable wallet event through `WalletEventListener`/`WalletEventHandler`, calls `BookingLifecycleService.confirmPayment`, confirms every held visit, changes the booking to `CONFIRMED`, and emits `BookingConfirmed` plus per-visit `VisitAssigned` events. Contact resolution must therefore happen in Booking Service before its call to Wallet Service, and `home_contact_frozen_at` must be set when confirmation succeeds.

## Worker arrival and start

`WorkerJobController` exposes:

- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/arrive`, with latitude, longitude, and accuracy.
- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/start`, currently with no request body.
- `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/complete`.

The gateway supplies `X-Authenticated-Worker-Id`. `BookingLifecycleService` locks the booking and visit, requires a confirmed booking and the currently assigned worker, and accepts arrival only from `ASSIGNED`. It validates coordinate ranges, accuracy at or below `booking.arrival-accuracy-meters` (default 100 m), and distance at or below `booking.arrival-radius-meters` (default 200 m) from the verified service coordinates. The request currently has no capture timestamp, so freshness is not enforced by Booking Service today. A failed validation changes nothing. Repeated arrival is idempotent once the visit is `ARRIVED`.

Successful arrival stores the location evidence, changes the visit to `ARRIVED`, and appends `WorkerArrived` in the same transaction. Today, `/start` directly performs `ARRIVED -> IN_PROGRESS` and appends `ServiceStarted`; the feature must gate this transition and make OTP verification the atomic start operation when `booking.arrival-otp.required=true`.

## Booking outbox events

Booking Service writes `OutboxEvent` rows transactionally and publishes them through the existing outbox publisher. Existing lifecycle event types include `BookingHeld`, `WorkerOffersCreated`, `WorkerOfferAccepted`, `WorkerOfferRejected`, `WorkerOfferExpired`, `BookingAwaitingPayment`, `BookingConfirmed`, `VisitAssigned`, `VisitAssignmentChanged`, `WorkerArrived`, `ServiceStarted`, `ServiceCompleted`, `BookingCompleted`, `BookingExpired`, `BookingCancelled`, `BookingCustomerCancelled`, `BookingPlatformCancelled`, `VisitRefundRequested`, and `VisitRefunded`.

The current `WorkerArrived` payload contains only `bookingId`, `visitIndex`, `workerId`, and `arrivedAt`; it already avoids contact data. `ServiceStarted` likewise contains identifiers and occurrence time. The new `ArrivalOtpSent` and `VisitOtpLocked` events must preserve that identifiers-only rule. OTP digits, contact name, and any phone number must never enter the outbox or Kafka.

## Existing notification behavior

`NotificationTemplates` currently maps:

- `WorkerArrived` to title `Worker arrived`, body `Your worker has arrived at the service location.`, critical `true`.
- `ServiceStarted` to title `Service started`, body `Your booked home service is now in progress.`, critical `false`.

`NotificationEventListener` consumes booking and wallet topics. It resolves the booking owner through Booking Service and records durable customer notifications; selected assignment/cancellation/offer events are separately fanned out to workers with privacy-filtered payloads. `NotificationTransaction` stores the history row and creates a PUSH delivery for every notification plus an SMS delivery for templates marked critical.

`NotificationDeliveryScheduler` leases pending deliveries, sends FCM through `PushDeliveryAdapter` to active device registrations, and sends generic SMS through `SmsDeliveryAdapter` after resolving the authenticated recipient contact. Failures are released for bounded retry. FCM data is produced by `NotificationPushMessageFactory` and contains event/route identifiers and channel ID, not arbitrary event payload data. WebSocket clients stream the same durable notification snapshots from `/api/v1/notifications/live` or the worker equivalent, so recording the customer notification makes it available to both history and WebSocket while PUSH is delivered asynchronously.

The arrival OTP cannot use the generic critical-notification SMS path because its destination may be an alternate non-user contact and Twilio Verify must generate/check the code. It needs a separate arrival-verification dispatcher/provider. The booker alert should still use the durable notification history/PUSH/WebSocket path. Worker status should be a separate privacy-filtered worker notification and response field, never a generic customer SMS.

## Login OTP and Twilio Verify today

Customer login lives in User Service (`OtpAuthenticationService`); Worker login lives in Worker Service (`WorkerAuthenticationService`). Both depend on the platform-common `PhoneVerificationProvider` port. `PhoneVerificationAutoConfiguration` selects either `TwilioVerifyProvider` for `otp.provider=twilio-verify` or `LoggingPhoneVerificationProvider` for `otp.provider=logging`.

Twilio Verify uses `OtpProviderProperties` under `otp`: provider, base URL, account SID, auth token, API key SID/secret, Verify service SID, TTL, and debug exposure. `TwilioVerifyProvider.start` calls `/v2/Services/{serviceSid}/Verifications` with channel `sms`; `check` calls `/v2/Services/{serviceSid}/VerificationCheck`. Twilio generates and validates the digits. The logging adapter hashes its locally generated code in memory and exposes it only when explicitly configured for development.

Arrival verification must introduce a distinct `ArrivalOtpProvider` port and configuration namespace. It may reuse the underlying Twilio HTTP conventions and shared `RestClient.Builder`, but it must not reuse `PhoneVerificationProvider`, login challenge stores, or login state.

## Booker contact lookup and phone normalization

User Service owns `UserProfile.displayName` and `phoneE164`. It already exposes the internally authenticated `GET /internal/v1/users/{userId}/contact` endpoint through `InternalCustomerContactController`, backed by `CustomerContactService`. Its current `CustomerContactSnapshot` contains the user ID and phone only. Booking Service already has `userServiceUrl`, an internal service-auth client factory, and User Service client patterns in `MatchingClients`.

SELF resolution should extend the existing internal contact snapshot to add `displayName`, then call that endpoint from Booking Service using the existing internal client factory. This avoids a parallel profile API and never trusts client-supplied booker details.

The login services currently each have a private `normalize` helper that only trims and accepts an already canonical `+91[6-9][0-9]{9}` value. There is no shared flexible normalizer today. The feature should extract one platform-common Indian mobile normalizer and reuse it from login plus home-contact validation. It must accept canonical `+91`, `91`, leading `0`, or bare ten-digit forms and return `+91XXXXXXXXXX`, rejecting wrong length and a first digit outside 6–9.

## Migration and implementation reuse points

Booking Service migrations currently end at `V16__per_visit_duration.sql`; the next free version is `V17`.

The implementation should reuse and extend:

- `Booking`, `SlotHold`, `BookingRepository`, `SlotHoldRepository`, and their existing pessimistic-lock queries.
- `BookingLifecycleService` transactions for confirmation, arrival, reassignment, cancellation, completion, and snapshot mapping.
- `BookingPaymentService` as the single pre-payment contact-resolution gate.
- User Service's existing internal contact endpoint and service-auth filter.
- `OutboxWriter`, `OutboxPublisher`, and the existing booking event topic.
- Notification Service's durable history, FCM, WebSocket, delivery leasing, recipient resolution, and privacy-filtered worker fan-out.
- Platform-common's `RestClientConfiguration` and Twilio error mapping patterns, while keeping arrival OTP behind a separate port/state/configuration namespace.

No alternate user profile is created, no full destination is exposed to a client, and no OTP is generated before an explicit GPS-validated worker-arrival transaction.
