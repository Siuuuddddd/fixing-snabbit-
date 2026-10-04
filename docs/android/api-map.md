# Android screen-to-API map

This map was derived from the current Spring controllers, DTOs, enums, gateway routes/security, OTP services, exception handlers, Razorpay webhook flow, tracking endpoints, and worker endpoints. `ProblemDetail` means the backend's RFC 7807-style response with at least HTTP `status` and `detail`. Money fields are integer minor units.

Implementation status: screens 1–13 are implemented. Screens 11–13 use the worker-location snapshot/SSE stream, booking status, worker rating, cancellation quote, cancellation, and refund-breakdown fields. Live tracking replays the latest snapshot before reconnecting and sends `Last-Event-ID`; the current backend does not provide durable historical replay. Later board destinations still use foundation placeholders.

## Authentication and gateway rules

The gateway exposes customer auth at `/api/v1/auth/**` and worker auth at `/api/v1/worker-auth/**`. Those routes, health, staff auth, and the Razorpay webhook are public. Worker routes require a JWT with `WORKER`; normal customer `/api/v1/**` routes require `CUSTOMER`; staff routes use staff roles. The gateway removes inbound identity headers and injects trusted identity values from the verified JWT.

There is no refresh-token endpoint. The Android foundation's authenticator boundary therefore clears the session after an unrecoverable 401 rather than fabricating refresh behavior.

## Screen mapping

| # | Screen | Backend contract used | Auth | Main error/status handling |
|---:|---|---|---|---|
| 1 | Splash | No endpoint | None | Local startup/config/session state only |
| 2 | Onboarding | No endpoint | None | Local completion state only |
| 3 | Login + OTP | `POST /api/v1/auth/otp/request`, `POST /api/v1/auth/otp/verify`; worker flavor uses `/api/v1/worker-auth/otp/request` and `/verify` | Public | 401 invalid/expired OTP; 429 rate limit. Request remains generic for unknown/inactive accounts. |
| 4 | Home | `GET /api/v1/services`; household, favourites, Plus endpoints described below | Customer | 401/403; 404 user; 409 favourite/Plus rules |
| 5 | Service details | `GET /api/v1/services`; pricing is finalized through booking availability/hold | Customer | 422 booking rule; 409 unavailable slot |
| 6 | Address & serviceability | `GET/POST /api/v1/users/{userId}/addresses`, `DELETE .../{addressId}` | Customer and matching user identity | 403 identity mismatch; 404; 422 address/location validation |
| 7 | Slot selection | `POST /api/v1/bookings/availability` | Customer | 422 invalid date/duration/rules; service errors surfaced as `ProblemDetail` |
| 8 | Household preferences | `GET/PUT /api/v1/users/{userId}/household`; favourite add/remove endpoints | Customer and matching user | 403; 404; 409 favourite limit/duplicate; 422 invalid preferences |
| 9 | Booking summary | Re-check `POST /api/v1/bookings/availability`; then `POST /api/v1/bookings/match-and-hold` | Customer; `Idempotency-Key` on hold | 409 slot/offer/state conflict; 422 rules. Price and wallet/UPI values are labelled estimates until payment creation. |
| 10 | Finding worker | Poll `GET /api/v1/bookings/{bookingId}`; on `AWAITING_PAYMENT`, `POST /api/v1/bookings/{bookingId}/payments`; optional `DELETE .../hold` while waiting | Customer owner; `Idempotency-Key` on payment | Booking status and returned payment split are authoritative; 403/404/409/422/502 surfaced explicitly. |
| 11 | Live tracking | `GET /api/v1/bookings/{bookingId}/visits/{visitIndex}/worker-location`; SSE `GET .../worker-location/stream`; booking polling for completion | Customer owner | 403/404; 409 outside tracking window/no fresh location; 502/offline reconnect with latest-snapshot replay; stale after two minutes |
| 12 | Booking completed | `GET /api/v1/bookings/{bookingId}`; `POST /api/v1/ratings/workers/{workerId}` with `bookingId`, `visitIndex`, `stars`, optional `comment` | Customer owner | 1–5 stars; 404; 409 duplicate/invalid rating; only the completed assigned visit can be rated |
| 13 | Cancel booking | `GET /api/v1/bookings/{bookingId}/cancellation-quote`; `POST /api/v1/bookings/{bookingId}/cancellations` with optional `reason` | Customer owner | 403/404; 409 invalid/unstarted state; 422 rule; authoritative fee/refund fields; refund remains pending without a customer status endpoint |
| 14 | Add money | `GET /api/v1/wallets/{walletId}`; `POST /api/v1/wallets/{walletId}/top-ups` | Customer owner; `Idempotency-Key` for top-up | 404 wallet/payment; 409 duplicate/conflict; 422 amount/rule; 502 provider. Client success never credits money. |
| 15 | Snabbit Plus | `GET /api/v1/users/{userId}/plus`; `POST /api/v1/users/{userId}/plus/purchases` | Customer and matching user; `Idempotency-Key` | 404; 409 subscription; wallet/payment failures |
| 16 | My bookings | No booking-list endpoint | Customer | Recorded in `api-gaps.md`; individual `GET /bookings/{id}` is insufficient |
| 17 | Notifications | `GET /api/v1/notifications`; `POST /api/v1/notifications/{notificationId}/read`; device registration/removal endpoints | Customer or worker, own notifications | 403/404; 422 invalid device. No notification SSE contract. |
| 18 | Profile | Address, household, favourites, Plus endpoints only | Customer/worker | General profile read/update is missing |
| 19 | Empty/error/offline | No endpoint | None | Shared presentation for transport, `ProblemDetail`, empty and offline states |
| 20 | Worker app landing | Worker OTP plus availability/jobs contracts | Worker | 401/403; no self-registration contract |
| 21 | Worker job feed | `GET /api/v1/worker/bookings/offers`; `GET /api/v1/worker/bookings/jobs`; accept/reject endpoints | Worker | 404 offer/job; 409 offer expired/already resolved/state conflict |
| 22 | Worker job details & flow | Accept/reject plus visit arrive/start/complete endpoints; availability and location endpoints | Worker assigned to job | 403/404; 409 invalid transition; 422 availability/location |
| 23 | Worker earnings | No earnings endpoint | Worker | Recorded in `api-gaps.md` |

## Request and response contracts

### OTP

| Method and path | Request fields | Success response fields |
|---|---|---|
| `POST /api/v1/auth/otp/request` | `phoneNumber` | `status`, `expiresInSeconds`, optional test-only `debugOtp` |
| `POST /api/v1/auth/otp/verify` | `phoneNumber`, `otp` | `accessToken`, `tokenType`, `expiresInSeconds` |
| `POST /api/v1/worker-auth/otp/request` | `phoneNumber` | Same shape as customer request |
| `POST /api/v1/worker-auth/otp/verify` | `phoneNumber`, `otp` | Same token shape; JWT role is worker |

There is no signup request, account-type discovery, or refresh response.

### User, household, favourites, and Plus

| Method and path | Request | Response/use |
|---|---|---|
| `GET /api/v1/users/{userId}/addresses` | Path `userId` | Saved address snapshots |
| `POST /api/v1/users/{userId}/addresses` | Address label and location/address fields defined by the backend DTO | Created address snapshot |
| `DELETE /api/v1/users/{userId}/addresses/{addressId}` | Path IDs | No-content success |
| `GET /api/v1/users/{userId}/household` | Path `userId` | Pets, food/utensil preferences, language/accessibility and work constraints |
| `PUT /api/v1/users/{userId}/household` | Household preference fields | Updated household snapshot |
| `POST /api/v1/users/{userId}/favourites/{workerId}` | Path IDs | Updated favourite state; maximum three is enforced |
| `DELETE /api/v1/users/{userId}/favourites/{workerId}` | Path IDs | Updated favourite state |
| `GET /api/v1/users/{userId}/plus` | Path `userId` | Membership/subscription snapshot |
| `POST /api/v1/users/{userId}/plus/purchases` | `walletId`, `planCode`; `Idempotency-Key` header | Purchase/membership result |

The Android client obtains `userId` from the access-token `sub` claim returned by OTP verification. Address creation sends `city = Mumbai`, a `400xxx` pincode, and a consented device-location sample with latitude, longitude, accuracy, and ISO-8601 capture time. The backend validates freshness, accuracy, Mumbai bounds, and configured forward/reverse geocoding; client-side checks are guidance only.

### Services and booking

| Method and path | Request | Response/use |
|---|---|---|
| `GET /api/v1/services` | None | Available service catalog and booking/pricing inputs |
| `POST /api/v1/bookings/availability` | `customerId`, `serviceId`, `bookingType`, `durationHours`, `addressId`, `startsAt` | Per-visit availability, compatible worker count and lowest price under 8 AM–7 PM and 1–4 hour rules |
| `POST /api/v1/bookings/match-and-hold` | The same exact request; `Idempotency-Key` | Booking/visit snapshots in `AWAITING_WORKER`, with offers and price snapshot |
| `GET /api/v1/bookings/{bookingId}` | Booking ID | Current booking snapshot/status |
| `DELETE /api/v1/bookings/{bookingId}/hold` | Booking ID | Releases a held booking when allowed |
| `POST /api/v1/bookings/{bookingId}/payments` | `walletId`, `useWallet`; `Idempotency-Key`. The client never supplies amounts. | Payment snapshot containing authoritative total, wallet amount, online amount, status and provider order ID |
| `GET /api/v1/bookings/{bookingId}/cancellation-quote` | Booking ID | Refund/fee quote before cancellation |
| `POST /api/v1/bookings/{bookingId}/cancellations` | Cancellation reason/request fields | Cancelled booking/refund result |

Booking types serialize exactly as `INSTANT`, `SCHEDULED`, and `MULTI_DAY`. Instant/scheduled requests contain one start; multi-day contains 2–7 distinct local dates. Availability is advisory, so match-and-hold repeats validation authoritatively. Payment can start only after worker acceptance changes the booking to `AWAITING_PAYMENT`; device/provider callbacks never prove confirmation.

### Tracking and ratings

| Method and path | Request | Response/use |
|---|---|---|
| `GET /api/v1/bookings/{bookingId}/visits/{visitIndex}/worker-location` | Path booking and visit index | Latest worker coordinates/timestamp snapshot |
| `GET /api/v1/bookings/{bookingId}/visits/{visitIndex}/worker-location/stream` | SSE request | Live location events; client reconnects and treats timestamps as freshness evidence |
| `POST /api/v1/ratings/workers/{workerId}` | Rating value and optional comment fields from DTO | Customer-to-worker rating snapshot |
| `POST /api/v1/worker/ratings/customers/{customerId}` | Rating request DTO | Worker-to-customer rating snapshot |

The location stream emits `worker-location` events whose event ID is `locationId` and otherwise emits keep-alive comments. Android sends the last received ID when reconnecting, fetches the latest REST snapshot as replay, deduplicates by location ID, and uses the backend two-minute freshness limit. The server currently starts each connection from its latest polled value and does not replay a retained event history.

Cancellation quotes return `plusActive`, `cancellationFeeMinor`, `refundAmountMinor`, `policyBand` (`EARLY` or `LATE`), and `calculatedAt`. Cancellation returns the booking snapshot with cancellation/refund amounts; it does not prove that the external refund has settled.

### Wallet and Razorpay top-up

| Method and path | Request | Response/use |
|---|---|---|
| `GET /api/v1/wallets/{walletId}` | Wallet ID | Wallet snapshot including available/reserved/spent totals exposed by backend |
| `POST /api/v1/wallets/{walletId}/top-ups` | `amountMinor`; `Idempotency-Key` | Pending top-up snapshot including provider order reference |
| Razorpay webhook route | Provider-signed event | Backend verifies signature and publishes/processes success once |

The app opens the provider flow using the server-created order. Only the signed webhook causes an append-only wallet credit. The app must show pending/unknown until server state proves success; a top-up read/status endpoint is currently missing.

### Notifications and support

| Method and path | Request | Response/use |
|---|---|---|
| `GET /api/v1/notifications` | Identity from JWT | Notification history |
| `POST /api/v1/notifications/{notificationId}/read` | Notification ID | Updated/read acknowledgement |
| `POST /api/v1/notifications/devices` | Device token and platform fields | Registers a push destination |
| `DELETE /api/v1/notifications/devices/{deviceId}` | Device ID | Removes a push destination |
| `POST /api/v1/support/tickets` | Ticket subject/category/details fields | New support ticket snapshot |
| `POST /api/v1/support/tickets/{ticketId}/messages` | Message body fields | Created support message |
| `GET /api/v1/support/tickets/{ticketId}/stream` | SSE request | Live support message stream |

### Worker jobs, availability, and location

| Method and path | Request | Response/use |
|---|---|---|
| `GET /api/v1/worker/bookings/offers` | Worker identity from JWT | Active job offers |
| `GET /api/v1/worker/bookings/jobs` | Worker identity | Assigned/current job snapshots |
| `POST /api/v1/worker/bookings/offers/{offerId}/accept` | Offer ID | Accepted/assigned booking result |
| `POST /api/v1/worker/bookings/offers/{offerId}/reject` | Offer ID | Rejected acknowledgement |
| `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/arrive` | Path IDs | Updated visit state |
| `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/start` | Path IDs | Updated visit state |
| `POST /api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/complete` | Path IDs | Updated visit/booking state |
| `GET /api/v1/workers/me/availability` | Worker identity | Availability records |
| `PUT /api/v1/workers/me/availability/{date}` | Date plus availability DTO | Updated date availability |
| `POST /api/v1/workers/me/location` | Coordinates/location timestamp fields | Accepted latest location |

## Error mapping policy

The client maps transport/offline/timeouts separately from server responses. Known backend statuses include 401 invalid authentication, 403 identity/role mismatch, 404 missing resource, 409 slot/offer/state/idempotency/rating/subscription conflicts, 422 booking/address/location/availability/wallet rules, 429 OTP throttling, and 502 payment/tracking/provider failures. The UI shows backend `detail` when safe and actionable, logs no credentials, and never branches on backend exception package/class names.
