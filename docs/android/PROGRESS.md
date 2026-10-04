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

## Real screens — deliberately not implemented in this milestone

- [ ] Splash animation
- [ ] Onboarding
- [ ] Customer/worker role choice and OTP login
- [ ] Customer home
- [ ] Service details
- [ ] Address and serviceability
- [ ] Slot selection
- [ ] Household preferences
- [ ] Booking summary and payment
- [ ] Worker matching/assigned
- [ ] Live tracking
- [ ] Booking completion/rating
- [ ] Cancellation/refund quote
- [ ] Wallet and Add Money states
- [ ] Snabbit Plus
- [ ] Booking list
- [ ] Notifications
- [ ] Customer profile
- [ ] Empty/error/offline presentation
- [ ] Worker landing/schedule
- [ ] Worker job feed
- [ ] Worker job details/progress
- [ ] Worker earnings

## Verification record

- Backend baseline: repository-root `.\gradlew.bat build --no-daemon --stacktrace` — passed (55 actionable tasks).
- Android foundation: `android\.\gradlew.bat clean build --no-daemon` — passed for customer and worker debug/release variants, unit tests, and lint (194 actionable tasks).
- A final repository-root backend regression build is run immediately before commit.
