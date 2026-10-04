# Android frontend progress

## Foundation milestone

- [x] Standalone Android Gradle project under `android/`
- [x] Customer and worker product flavors
- [x] Kotlin, Compose Material 3, and Inter typography
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
- [x] Real customer and worker OTP endpoints with exact backend DTO field names
- [x] Encrypted access-token persistence after successful verification
- [x] Resend cooldown, loading, validation, offline, throttling, server, and invalid/expired-code states
- [x] ViewModel, launch-routing, repository, and endpoint-contract unit tests for both flavors

## Remaining real screens

- [x] Customer home with backend catalogue, bottom navigation, pull-to-refresh, empty, offline, and error states
- [x] Service details with typed service navigation, backend catalogue pricing, duration selection, and pull-to-refresh
- [x] Address and serviceability with JWT-subject ownership, saved-address list/create/delete, runtime location consent, and backend verification
- [x] Screens 4–6 ViewModel, repository, identity, route, and endpoint-contract unit tests
- [x] Slot selection for instant, scheduled, and 2–7 day bookings with authoritative availability
- [x] Household preferences read/update with exact matching constraint values
- [x] Booking summary with advisory wallet/UPI estimate and idempotent match-and-hold
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
- [ ] Worker landing/schedule
- [ ] Worker job feed
- [ ] Worker job details/progress
- [ ] Worker earnings

## Verification record

- Backend baseline: repository-root `.\gradlew.bat build --no-daemon --stacktrace` — passed (55 actionable tasks).
- Android foundation: `android\.\gradlew.bat clean build --no-daemon` — passed for customer and worker debug/release variants, unit tests, and lint (194 actionable tasks).
- Authentication milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, unit tests, and lint (195 actionable tasks).
- Catalogue/address milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 32 unit tests, and lint (195 actionable tasks).
- Booking/matching milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 38 unit tests, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon` passed (55 actionable tasks).
- Tracking/completion/cancellation milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 43 unit tests, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon` passed (55 actionable tasks).
- Wallet/Plus milestone: Razorpay checkout is activity-owned; client callbacks only begin backend verification. `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 46 unit tests, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon` passed (55 actionable tasks).
- Bookings/notifications/profile/shared-states milestone: `android\.\gradlew.bat clean build --no-daemon` passed for customer and worker debug/release variants, 52 unit tests, and lint (195 actionable tasks); repository-root `\.\gradlew.bat build --no-daemon` passed (55 actionable tasks).
- A final repository-root backend regression build is run immediately before commit.
