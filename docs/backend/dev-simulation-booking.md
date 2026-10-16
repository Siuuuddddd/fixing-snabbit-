# Development booking simulation

## Scope

This workflow is local/development-only. `dev-simulation.enabled` defaults to `false`, the scheduler is restricted to the `local` and `dev` profiles, and the production configuration guard rejects an enabled simulation.

The customer flow is: create atomic slot holds, select one worker per visit, capture the home contact, pay once, wait for the persisted arrival deadline, verify the arrival OTP, start the visit, and let development auto-completion finish it. Multi-day visits may use different workers and durations.

## Recurring worker availability

`worker_availability_rule` stores a stable worker, period, first start, and last start. Rules recur every day. The local seed contains:

- Morning, 08:00–11:45: Savita, Vineeta, Sangeeta, Shivani
- Afternoon, 12:00–15:45: Deepika, Asha, Sana, Alisha
- Evening, 16:00–19:00: Sailee, Vaishali, Deepika

A rule constrains the visit start only; a one-to-four-hour visit may finish after the period. Active overlapping holds and confirmed visits make the worker unavailable. Existing tiers, ratings, tenure, compatibility, skills, and Plus favourites remain authoritative.

## Customer endpoints

- `GET /api/v1/bookings/{bookingId}/worker-options?visitIndex={n}` returns `bookingId`, `visitIndex`, `favourites`, and `others`. Worker items expose identity, first name, tier, rating/count, distance, surcharge, and per-visit price; they never expose a phone number.
- `PUT /api/v1/bookings/{bookingId}/worker-selection` accepts `visitIndex` and `workerId`. It rechecks availability and replaces only that visit's hold. Payment returns `WORKER_SELECTION_REQUIRED` until every visit has a worker.
- `PUT /api/v1/bookings/{bookingId}/home-contact` accepts `SELF` or `OTHER`, with optional name and Indian phone. Responses mask the number.
- `POST /api/v1/users/{userId}/phone-verification/send` accepts `phoneNumber`; it returns only the masked phone and retry delay.
- `POST /api/v1/users/{userId}/phone-verification/verify` accepts `phoneNumber` and a six-digit `code`. Verified numbers are persisted with `phone_verified_at`; collisions return `PHONE_IN_USE`.
- `POST /api/v1/bookings/{bookingId}/visits/{visitIndex}/arrival-otp/verify` accepts `code` and is restricted to the booking owner.
- `POST /api/v1/bookings/{bookingId}/visits/{visitIndex}/arrival-otp/resend` invalidates the previous provider code and resets local wrong-attempt state. Limits return `OTP_LIMIT_REACHED`.

Booking reads expose `serverNow`, masked home-contact phone, and per-visit `arrivalDueAt`, job status, and selected-worker summary. OTP digits and full phone numbers are never returned in booking responses or events.

## Arrival state machine

Payment confirmation confirms every visit. With simulation enabled, instant and scheduled bookings receive `arrival_due_at = confirmed_at + delay`. Sequential multi-day bookings schedule the first visit and schedule each next visit after the previous visit completes; `SIMULTANEOUS` schedules all visits at confirmation.

Every five seconds the scheduler claims due rows using `FOR UPDATE SKIP LOCKED`. It records simulated evidence, marks the visit arrived, creates a fresh challenge, and emits identifier-only outbox events. Delivery begins only after arrival. Verification is single-use and starts the service. Development auto-completion advances in-progress visits to completed.

## Configuration names

- `DEV_SIMULATION_ENABLED`
- `DEV_SIMULATION_SCAN_DELAY_MS`
- `DEV_ARRIVAL_DELAY_SECONDS`
- `DEV_AUTO_COMPLETE_DELAY_SECONDS`
- `DEV_MULTI_DAY_ARRIVAL_MODE`
- Existing Twilio Verify variables remain unchanged.

## Android behavior

Only the customer flavor is buildable. The booking progress screen loads worker choices for each visit, shows Plus favourites before other available workers, captures the optional home contact, initiates payment only after an explicit tap, polls authoritative booking state, computes the countdown from `arrivalDueAt` and `serverNow`, and exposes arrival OTP verify/resend actions. The customer debug unit tests and APK assembly cover the resulting variant.

## Gaps and follow-up cleanup

- Backend worker-auth endpoints remain available for compatibility. Remove them in a dedicated migration after all callers have moved away.
- Worker-only Android source remains in the repository but has no worker build flavor. Deleting the unused source safely requires a separate cleanup because existing unrelated work and tests currently reference it.
- Phone transfer is deliberately conservative: a freshly proven phone already owned by another record returns generic `PHONE_IN_USE`. Automated transfer requires a cross-service proof that the old record has no bookings, entitlements, or favourites; it must then write `customer_phone_transfer_audit` atomically.
- The customer UI currently hosts worker selection, checkout, countdown, and arrival confirmation as states of one booking-progress destination. Splitting these into separately addressable routes and adding push/deep-link restoration remains a UI follow-up.
- Emulator/Twilio end-to-end verification requires a running local stack, a verified Twilio trial recipient, and payment-webhook credentials; no secret values belong in this document.

Unrelated existing Android, wallet-service, Gradle, local configuration, generated output, and existing documentation changes were intentionally left uncommitted by this work.
