# Android frontend progress

## Foundation milestone

- [x] Standalone Android Gradle project under `android/`
- [x] Customer and worker product flavors
- [x] Kotlin, Compose Material 3, and centrally switched system-sans typography for both flavors (legal Roboto fallback pending licensed Google Sans brand files)
- [x] Koin modules for storage, networking, repositories, ViewModels, and Navigation 3 entries
- [x] Retrofit, OkHttp, kotlinx serialization, auth interceptor, redacted logging, and 401 authenticator boundary
- [x] Encrypted access-token storage
- [x] Navigation 3 shell with independent bottom-tab back stacks
- [x] Typed routes for all 23 design-board destinations
- [x] Shared color, typography, spacing, shape, component, and preview foundation
- [x] API map, UI specification, API gaps, asset backlog, and working rules
- [x] Local secret placeholders are ignored; Android/reference artifacts are excluded from Docker context

## Authentication milestone

- [x] Splash animation with persisted-session routing and auto-login
- [x] Three-page onboarding with persisted completion state
- [x] Flavor-aware customer/worker phone login and OTP verification
- [x] Twilio Verify real-SMS OTP, post-verification customer self-registration, and verified existing-worker enforcement
- [x] Real customer and worker OTP endpoints with exact backend DTO field names
- [x] Encrypted access-token persistence after successful verification
- [x] Resend cooldown, loading, validation, offline, throttling, server, and invalid/expired-code states
- [x] ViewModel, launch-routing, repository, and endpoint-contract unit tests for both flavors

## Remaining real screens

- [x] Customer home with backend catalogue, bottom navigation, pull-to-refresh, empty, offline, and error states
- [x] Customer home redesigned to the reference hierarchy: selected-address header with wallet/rewards/profile actions, Schedule and Instant cards, favourite-expert banner, responsive 3×2 service grid, and habit-pack strip. Catalogue IDs and the selected address remain backend-backed; favourites and promo are isolated, documented fake repositories until APIs exist, and all unavailable art uses named placeholders.
- [x] Service details with typed service navigation, backend catalogue pricing, duration selection, and pull-to-refresh
- [x] Address and serviceability with JWT-subject ownership, saved-address list/create/delete, runtime location consent, and backend verification
- [x] Screens 4–6 ViewModel, repository, identity, route, and endpoint-contract unit tests
- [x] Slot selection for instant, scheduled, and 2–7 day bookings with authoritative availability
- [x] Customer Schedule split into typed Navigation 3 date/duration and start-time pages with independently scoped ViewModels, retained Back state, duplicate-navigation protection, explicit 2–7 day Multi-day Apply, real 15-minute availability, backend-supported 1–4 hour pricing, sold-out/past-slot handling, a documented Habit Pack placeholder, and handoff to the existing booking-summary/payment flow.
- [x] Household preferences read/update with exact matching constraint values
- [x] Booking summary with advisory wallet/UPI estimate and idempotent match-and-hold
- [x] Booking summary isolates parallel-load failures, degrades an inaccessible prototype wallet to the existing wallet-unavailable state, and rejects invalid restored route arguments without crashing
- [x] Worker matching polling, hold cancellation, post-acceptance payment creation, and authoritative split
- [x] Live tracking with latest-snapshot replay, SSE `Last-Event-ID`, reconnect backoff, stale-location state, and completion transition
- [x] Booking completion with authoritative booking details and 1–5 star worker rating
- [x] Cancellation with authoritative early/late quote, Plus benefit, fee/refund breakdown, optional reason, and pending refund state
- [x] Wallet summary and Add Money with Razorpay test checkout, process-local prototype history, and backend-confirmed balance updates
- [x] Snabbit Plus status and purchase with prototype plan display and backend-confirmed membership activation
- [x] My Bookings with persisted subject-scoped known booking IDs and authoritative per-booking backend refresh
- [x] Notification history/read states, notification permission, and customer/worker FCM device registration
- [x] Customer/worker profile summary, backend-backed customer settings status, device cleanup, and logout
- [x] Shared loading, empty, recoverable error, and offline presentation used across real screens
- [x] Worker landing/schedule with seven-day backend availability and explicit day updates
- [x] Worker job feed with authoritative offers/jobs, expiry countdown, accept, decline, refresh, empty, offline, and error states
- [x] Worker job details with assigned/arrived/in-progress/completed transitions and consented foreground location publishing
- [x] Worker earnings gap state that does not invent totals or payout history while the backend contract is missing

## Firebase push milestone

- [x] Optional Google Services configuration supports both customer and worker flavor package names without breaking builds when the ignored file is absent
- [x] Firebase registration after login, refresh re-registration, logout removal, Android 13+ permission handling, and `bookings` / `payments` / `jobs` channels
- [x] Foreground notification display, authoritative history refresh, and authenticated Navigation 3 routing from notification taps
- [x] Local Docker FCM provider selection with an external read-only service-account mount and logging fallback
- [x] Customer booking/payment notification routing plus privacy-filtered worker offer, expiry, assignment, and cancellation pushes
- [x] Invalid Firebase destinations are deactivated while durable delivery leasing, retry, and idempotency remain intact

## Verification record

- Backend baseline: repository-root `.\gradlew.bat build --no-daemon --stacktrace` — passed (55 actionable tasks).
- Android foundation: `android\.\gradlew.bat clean build --no-daemon` — passed for customer and worker debug/release variants, unit tests, and lint (194 actionable tasks).
- Authentication milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, unit tests, and lint (195 actionable tasks).
- Catalogue/address milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 32 unit tests, and lint (195 actionable tasks).
- Booking/matching milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 38 unit tests, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon` passed (55 actionable tasks).
- Tracking/completion/cancellation milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 43 unit tests, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon` passed (55 actionable tasks).
- Wallet/Plus milestone: Razorpay checkout is activity-owned; client callbacks only begin backend verification. `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 46 unit tests, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon` passed (55 actionable tasks).
- Bookings/notifications/profile/shared-states milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 52 unit tests, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon` passed (55 actionable tasks).
- Worker screens milestone: worker OTP remains flavor-aware; availability, offer/job feeds, lifecycle transitions, and foreground location publishing use exact backend contracts, while earnings stays an explicit API-gap state. `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 57 unit tests per flavor, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon --stacktrace` passed (55 actionable tasks).
- Firebase push milestone: Notification and Support full-context smoke tests cover the shared `RestClient.Builder`; mocked FCM v1 tests cover authenticated payloads and invalid-token deactivation; Android tests cover customer/worker registration and push-route parsing. Local Docker health checks reported `UP` for the gateway and all seven services with the Firebase key mounted read-only.
- Firebase emulator verification: both debug flavors installed on a Pixel 9 Pro API 36 AVD, customer and worker OTP login completed, both role-specific devices registered, and real FCM v1 messages arrived through `payments` and `jobs`. Foreground delivery used the backend channel, Customer wallet and Worker job tap intents reached their Navigation 3 destinations, and filtered logcat showed no app crash or serialization failure.
- Final Firebase regression: repository-root `.\gradlew.bat build --no-daemon --max-workers=1` passed (55 actionable tasks); `android\.\gradlew.bat clean build --no-daemon` passed both flavors, unit tests, and lint (199 actionable tasks).
- Booking-summary crash regression: a customer-owned-wallet mismatch (`403 Authenticated customer does not own this wallet`) is contained as wallet unavailable while authoritative booking failures render the shared recoverable error state; invalid/missing summary-route arguments render validation instead of throwing. Focused customer tests passed, `android\.\gradlew.bat clean build --no-daemon` passed both flavors, unit tests, and lint (199 actionable tasks), and repository-root `.\gradlew.bat build --no-daemon --max-workers=1` passed (55 actionable tasks). The updated customer APK reached Home and Service Details with gateway `200` responses and no crash; final post-login booking-summary tapping was blocked when the pre-existing real-Twilio session expired and requires the user's private SMS OTP.
- Home redesign regression: focused Home use-case/ViewModel tests passed; `android\.\gradlew.bat build --no-daemon` passed both flavors, unit tests, and lint (198 actionable tasks), and repository-root `.\gradlew.bat build --no-daemon --max-workers=1` passed (55 actionable tasks). The customer debug APK installed and launched on the running emulator, but the preserved session had expired, so authenticated Home screenshot comparison requires a new private SMS OTP.
- Schedule redesign regression: focused Schedule domain, ViewModel, and Navigation 3 tests passed; customer debug assembly/tests/lint passed (56 actionable tasks), `android\.\gradlew.bat build --no-daemon` passed both flavors, unit tests, and lint (198 actionable tasks), and repository-root `.\gradlew.bat build --no-daemon --max-workers=1` passed (55 actionable tasks). Deterministic previews cover the progressive steps, dark mode, and 200% text; emulator screenshot comparison was unavailable because no emulator or device was running.
- Two-page Schedule/availability regression: the date-duration and start-time pages now have separate Navigation 3 keys and ViewModels, named 22sp/16sp Schedule typography, explicit Multi-day Apply, retained Back state, and duplicate-navigation protection. The local all-sold-out cause was the seeded customer's `HEARING_SUPPORT` household constraint not matching either seeded worker (with long-lived fixed-date availability/capacity also becoming stale); `local`-profile-only initializers now add the missing stable-worker capability and rolling seven-day rows without touching production profiles. Pixel 9 Pro API 36 checks reached Home and both Schedule pages for Today, Tomorrow, and Multi-day through real endpoints, showed selectable slots, and saved inspected screenshots under the git-ignored `docs/android/run-screenshots/`. Both-flavor unit tests/lint passed (75 actionable tasks), `android\.\gradlew.bat build --no-daemon` passed both debug/release flavors (198 actionable tasks), repository-root `.\gradlew.bat build --no-daemon --max-workers=1` passed (55 actionable tasks), ports 8080–8086 reported `UP`, and final filtered logcat contained no crash or serialization failure. Google Sans was not embedded because it is absent from the open Google Fonts catalogue; both flavors now share the legal system-sans/Roboto fallback behind one future replacement point.
