# Customer login methods

## Progress

- Customer phone OTP, Google, and email/password sign-in return the same customer session shape.
- `rememberMe=true` is the default. Remembered refresh sessions use the configured sliding 180-day idle window; unremembered sessions have a configurable 24-hour absolute lifetime.
- The Customer Android flavor has a single login entry screen for phone, Google, and email. Phone verification and the existing email login, registration, verification, forgot-password, and reset screens are connected through Navigation 3.
- Tokens use the existing encrypted, backup-excluded Android session store. User Service stores only refresh-token hashes.
- Worker Google authentication and staff phone OTP are unchanged.

## Customer API

All paths below are exposed through API Gateway. Session responses contain `accessToken`, `tokenType`, `expiresIn`, `refreshToken`, and `user`. The user object includes `id`, `name`, nullable `email`, nullable `phoneNumber`, `pictureUrl`, `isNewUser`, `emailVerified`, `hasPassword`, and `authProviders`.

### Phone OTP

`POST /api/v1/auth/otp/request` accepts:

```json
{ "phoneNumber": "${CUSTOMER_PHONE_E164}" }
```

Successful requests always return HTTP `202` with the same response, whether or not an account exists:

```json
{ "status": "REQUEST_ACCEPTED", "retryAfterSeconds": 30 }
```

Only Indian mobile numbers are accepted: ten national digits beginning with 6–9, optionally supplied with `+91`, `91`, or a leading `0`. The service enforces a 30-second resend cooldown, five sends per hour per number, five incorrect checks per challenge, and a separate per-IP limit. Redis keys use one-way digests; logs mask phone numbers and never contain codes.

`POST /api/v1/auth/otp/verify` accepts:

```json
{
  "phoneNumber": "${CUSTOMER_PHONE_E164}",
  "code": "${CUSTOMER_OTP}",
  "name": "Test Customer",
  "rememberMe": true,
  "deviceId": "optional-installation-id",
  "deviceName": "optional-device-name"
}
```

`name` is optional. A successful first verification creates an ACTIVE phone-only customer without onboarding; later verifications retrieve that customer. Five incorrect checks burn the challenge. For local demo data only, `DEV_SEED_DEMO_CUSTOMER_PHONE` may attach a successfully verified phone to the pre-provisioned `DEV_SEED_DEMO_CUSTOMER_EMAIL` profile when `DEV_SEED_ENABLED=true`.

### Google

`POST /api/v1/auth/google` accepts the verified Google ID token, nonce, optional device metadata, and `rememberMe`. Android uses Credential Manager with `GetGoogleIdOption.setFilterByAuthorizedAccounts(false)`, so the system account chooser can show every Google account on the device. The Web OAuth client ID is configured locally as `GOOGLE_WEB_CLIENT_ID_CUSTOMER` in `android/local.properties`; never put the client secret in the app.

The Google icon comes from Google's official [Sign in with Google branding resources](https://developers.google.com/identity/branding-guidelines). The checked-in raster is used unchanged as an image asset.

### Email and password

The existing customer endpoints are:

| Method and path | Purpose | Result |
| --- | --- | --- |
| `POST /api/v1/auth/register` | Create a pending account and send a verification code | Generic `202` |
| `POST /api/v1/auth/verify-email` | Verify the six-digit code and create a session | Session |
| `POST /api/v1/auth/resend-verification` | Send another verification code | Generic `202` |
| `POST /api/v1/auth/login` | Sign in with a verified email and password | Session |
| `POST /api/v1/auth/forgot-password` | Send a reset code | Generic `202` |
| `POST /api/v1/auth/reset-password` | Verify reset code, set password, revoke sessions | `204` |
| `POST /api/v1/auth/change-password` | Change password while authenticated | Replacement session |
| `GET /api/v1/auth/me` | Read the authenticated customer | User |

Registration accepts name, email, password, and accepted terms. Email verification and login accept `rememberMe`, defaulting to `true`. Verification codes are HMAC-protected, valid for ten minutes, limited to five checks, and subject to resend and identity/IP throttles. Passwords use the configured strong password encoder.

Session lifecycle endpoints remain `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, and `POST /api/v1/auth/logout-all`.

### Error codes

Android maps the following stable codes to user-facing states:

- `VALIDATION_ERROR`
- `INVALID_OR_EXPIRED_CODE`
- `TOO_MANY_ATTEMPTS`
- `RATE_LIMITED` (with `retryAfterSeconds` when available)
- `EMAIL_NOT_VERIFIED`
- `INVALID_CREDENTIALS`
- `WEAK_PASSWORD`
- `ACCOUNT_DISABLED`
- `SESSION_REVOKED`
- `OTP_PROVIDER_UNAVAILABLE`

Provider cancellation is silent in the Google flow. Missing Google configuration, no device account, offline requests, and server failures remain local Android states rather than new API codes.

## Configuration variable names

Android `local.properties`:

- `GOOGLE_WEB_CLIENT_ID_CUSTOMER`
- `GOOGLE_WEB_CLIENT_ID_WORKER` (existing Worker configuration; unchanged)

Backend environment:

- `OTP_PROVIDER`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`
- `TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET`, `TWILIO_VERIFY_SERVICE_SID`
- `AUTH_SHORT_SESSION_ABSOLUTE_TTL`, `AUTH_REFRESH_IDLE_TTL`, `AUTH_REFRESH_ABSOLUTE_TTL`
- `DEV_SEED_ENABLED`, `DEV_SEED_DEMO_CUSTOMER_EMAIL`, `DEV_SEED_DEMO_CUSTOMER_PHONE`
- `AUTH_CODE_HMAC_SECRET`, `OTP_PEPPER`
- `AUTH_EMAIL_SENDER`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_AUTH`, `SMTP_TLS_ENABLED`
- `AUTH_EMAIL_RATE_LIMIT_WINDOW`, `AUTH_EMAIL_RATE_LIMIT_PER_IDENTITY`, `AUTH_EMAIL_RATE_LIMIT_PER_IP`, `AUTH_EMAIL_RATE_LIMIT_MAX_DELAY`

Production requires Twilio Verify and real SMTP configuration. The startup guard rejects the local provider, Mailpit hosts, development seeds, or missing required secrets.

## Local testing with Mailpit

1. From the repository root, start the stack with `docker compose -f compose.yaml -f compose.apps.yaml up -d`.
2. Confirm API Gateway and User Service health endpoints report `UP`.
3. Open `http://localhost:8025` to view Mailpit. Its SMTP listener is bound to `127.0.0.1:1025` and its web UI to `127.0.0.1:8025`.
4. Register using a generated, non-personal test address through the Customer app. Read the six-digit verification or reset code from Mailpit, then complete the flow in the app.
5. For phone testing, configure Twilio Verify. Trial accounts send only to recipient numbers verified in Twilio. Never enable code exposure outside tests.

## Identity rules and gaps

- Phone accounts are keyed only by verified E.164 phone number; Google and email/password accounts are keyed by verified email identity.
- Independently created phone and email/Google accounts are not merged automatically. A future authenticated, re-verification-based linking flow is required.
- Staff authentication still uses phone OTP and needs its own replacement if the product later standardizes staff on another provider.
- The repository has no Customer destinations for Terms of Service, Privacy Policy, or Content Policy, so the three dotted-underlined labels are non-clickable pending product URLs/routes.
- A real Google sign-in requires a Google account on the emulator and correct Google Cloud OAuth configuration. Twilio trial delivery is limited by the Twilio account.
- Copy this API summary into `docs/android/api-map.md` manually after its unrelated in-progress edits are resolved.

## Verification

- Backend focused tests: passed.
- Customer Android unit tests: passed.
- Worker debug APK: passed (`assembleWorkerDebug`).
- Pixel 9 Pro Google Play emulator: the login screen installed, launched, and matched the reference structure; banner cropping, spacing, controls, disabled state, footer, and email navigation were inspected.
- API Gateway and User Service health: `UP` after rebuilding both images. The public phone route reaches request validation without authentication.
- Real Google authentication was not completed because `GOOGLE_WEB_CLIENT_ID_CUSTOMER` is absent locally. A real SMS verification was not completed; it depends on the configured Twilio account and trial-recipient rules.
- The emulator became temporarily unresponsive while returning from Android's Google-account setup UI. This happened outside the app's login UI; no app crash was recorded.

## Files left uncommitted

The following pre-existing or unrelated paths remain unstaged after the final audit. Directory entries are shown exactly as Git reports them:

```text
android/app/src/main/java/com/snabbit/prototype/mobile/data/booking/DefaultBookingRepository.kt
android/app/src/main/java/com/snabbit/prototype/mobile/data/booking/remote/BookingApi.kt
android/app/src/main/java/com/snabbit/prototype/mobile/data/cancellation/CancellationData.kt
android/app/src/main/java/com/snabbit/prototype/mobile/data/network/NetworkClientFactory.kt
android/app/src/main/java/com/snabbit/prototype/mobile/data/network/SessionTokenRefresher.kt
android/app/src/main/java/com/snabbit/prototype/mobile/data/network/UnsupportedTokenRefresher.kt
android/app/src/main/java/com/snabbit/prototype/mobile/data/wallet/WalletData.kt
android/app/src/main/java/com/snabbit/prototype/mobile/di/AppModules.kt
android/app/src/main/java/com/snabbit/prototype/mobile/domain/booking/BookingRepository.kt
android/app/src/main/java/com/snabbit/prototype/mobile/domain/wallet/WalletRepository.kt
android/app/src/main/java/com/snabbit/prototype/mobile/navigation/CustomerNavigation.kt
android/app/src/main/java/com/snabbit/prototype/mobile/navigation/Routes.kt
android/app/src/main/java/com/snabbit/prototype/mobile/navigation/SnabbitApp.kt
android/app/src/main/java/com/snabbit/prototype/mobile/ui/booking/BookingSummaryScreen.kt
android/app/src/main/java/com/snabbit/prototype/mobile/ui/booking/FindingWorkerScreen.kt
android/app/src/main/java/com/snabbit/prototype/mobile/ui/booking/ScheduleDateDurationScreen.kt
android/app/src/main/java/com/snabbit/prototype/mobile/ui/booking/ScheduleDateDurationViewModel.kt
android/app/src/main/java/com/snabbit/prototype/mobile/ui/booking/ScheduleStartTimeScreen.kt
android/app/src/main/java/com/snabbit/prototype/mobile/ui/booking/ScheduleStartTimeViewModel.kt
android/app/src/main/res/values/strings.xml
android/app/src/test/java/com/snabbit/prototype/mobile/data/booking/
android/app/src/test/java/com/snabbit/prototype/mobile/data/bookings/
android/app/src/test/java/com/snabbit/prototype/mobile/data/cancellation/
android/app/src/test/java/com/snabbit/prototype/mobile/data/household/
android/app/src/test/java/com/snabbit/prototype/mobile/data/network/NetworkClientFactoryTest.kt
android/app/src/test/java/com/snabbit/prototype/mobile/data/network/UnsupportedTokenRefresherTest.kt
android/app/src/test/java/com/snabbit/prototype/mobile/data/plus/
android/app/src/test/java/com/snabbit/prototype/mobile/data/profile/DefaultProfileRepositoryTest.kt
android/app/src/test/java/com/snabbit/prototype/mobile/data/rating/
android/app/src/test/java/com/snabbit/prototype/mobile/data/tracking/DefaultTrackingRepositoryTest.kt
android/app/src/test/java/com/snabbit/prototype/mobile/data/wallet/
android/app/src/test/java/com/snabbit/prototype/mobile/data/worker/DefaultWorkerRepositoriesTest.kt
android/app/src/test/java/com/snabbit/prototype/mobile/navigation/RoutesTest.kt
android/app/src/test/java/com/snabbit/prototype/mobile/ui/booking/ScheduleDateDurationViewModelTest.kt
android/app/src/test/java/com/snabbit/prototype/mobile/ui/booking/ScheduleStartTimeViewModelTest.kt
android/app/src/test/java/com/snabbit/prototype/mobile/ui/worker/WorkerViewModelsTest.kt
android/gradle.properties
android/gradle/gradle-daemon-jvm.properties
compose.local-codex.yaml
docs/Snabbit-Backend-Walkthrough.pdf
docs/android/PROGRESS.md
docs/android/README.md
docs/android/api-gaps.md
docs/android/api-map.md
docs/android/assets-todo.md
docs/android/conversation-handoff.md
docs/android/ui-spec.md
docs/rendered-walkthrough-final/
docs/rendered-walkthrough/
gradle.properties
gradle/gradle-daemon-jvm.properties
it duration to multi-day bookings (backend)\uf022
local.properties
output/
settings.gradle.kts
tmp/
wallet-service/src/main/kotlin/com/snabbit/prototype/wallet/controller/WalletController.kt
wallet-service/src/main/kotlin/com/snabbit/prototype/wallet/repository/WalletAccountRepository.kt
wallet-service/src/main/kotlin/com/snabbit/prototype/wallet/service/WalletService.kt
wallet-service/src/main/kotlin/com/snabbit/prototype/wallet/service/WalletTransactionService.kt
wallet-service/src/test/kotlin/com/snabbit/prototype/wallet/service/WalletTransactionServiceTest.kt
```
