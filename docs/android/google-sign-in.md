# Android Google sign-in

## What was built

- Customer and Worker now use a shared Compose login screen with a full-width **Continue with Google** button backed by AndroidX Credential Manager and `GetSignInWithGoogleOption`.
- Each sign-in attempt creates a cryptographically random nonce. The Google ID token and nonce are sent only to the correct backend endpoint for the active flavor.
- The OAuth Web client ID is flavor-specific and read from git-ignored `android/local.properties`. A blank value is safe: the Google button is disabled and the screen explains that sign-in is not configured.
- **Add a Google account** is always available, including while the OAuth client ID is blank. It opens Android's account-add screen; it does not bypass the required OAuth setup.
- Customer first-time sign-in opens Home directly. The marketing onboarding remains independent from registration.
- Worker shared development sign-in supports the backend worker picker. The picker shows first name and tier and reacquires a fresh Google credential before profile selection.
- Access token, rotating refresh token, expiry, and non-secret Google identity metadata are stored in encrypted shared preferences and excluded from backup/device transfer.
- Protected requests receive the bearer token through the existing OkHttp stack. A single-flight refresher persists the rotated session before retrying a request, retries `REFRESH_IN_PROGRESS`, preserves the session on network failure, and clears it on revocation/non-retryable client failure.
- Refresh runs on app startup and shortly before token expiry. Backend logout/logout-all are wired to both profiles, local cleanup always runs, Credential Manager state is cleared, and navigation returns to Login.
- FCM registration remains active after login and local FCM/device state is cleared during logout. The Customer profile shows the stored Google name, email and picture and can save an optional Indian mobile number.
- A `PHONE_REQUIRED` response raises an app-level phone dialog. The number is normalized to `+91` after validating ten digits beginning with 6-9.
- Customer and Worker phone/OTP request, verification and registration endpoints, routes, strings and tests were removed.

## Backend contract used

| Purpose | Method and path | Request |
| --- | --- | --- |
| Customer Google sign-in | `POST /api/v1/auth/google` | `idToken`, optional `nonce`, `deviceId`, `deviceName` |
| Worker Google sign-in | `POST /api/v1/worker-auth/google` | same shape |
| Select worker | `POST /api/v1/worker-auth/select-profile` | fresh `idToken`, optional `nonce`, `selectionToken`, `workerId`, optional device fields |
| Switch worker | `POST /api/v1/worker-auth/switch-profile` | `workerId` |
| Customer refresh | `POST /api/v1/auth/refresh` | `refreshToken` |
| Worker refresh | `POST /api/v1/worker-auth/refresh` | `refreshToken` |
| Customer logout | `POST /api/v1/auth/logout` or `/logout-all` | bearer token |
| Worker logout | `POST /api/v1/worker-auth/logout` or `/logout-all` | bearer token |
| Customer phone | `PUT /api/v1/users/{userId}/phone` | `phoneNumber` |

A session response contains `accessToken`, `tokenType`, `expiresIn`, `refreshToken`, and `user` (`id`, `name`, `email`, nullable `pictureUrl`, `isNewUser`, `role`). A worker-picker response contains `code`, `selectionToken`, `expiresIn`, `workers` (`workerId`, first `name`, `tier`), and `requiresWorkerSelection`.

Handled ProblemDetail codes: `INVALID_GOOGLE_TOKEN`, `EMAIL_NOT_VERIFIED`, `WORKER_NOT_REGISTERED`, `WORKER_SELECTION_REQUIRED`, `INVALID_SELECTION_TOKEN`, `ACCOUNT_DISABLED`, `ACCOUNT_LINK_CONFLICT`, `SESSION_REVOKED`, `REFRESH_IN_PROGRESS`, `RATE_LIMITED`, `PHONE_REQUIRED`, and `SHARED_WORKER_SESSION_REQUIRED`.

## Local configuration

Add these names to `android/local.properties`; do not commit their values:

```properties
GOOGLE_WEB_CLIENT_ID_CUSTOMER=<customer Web OAuth client ID>
GOOGLE_WEB_CLIENT_ID_WORKER=<worker Web OAuth client ID>
```

Use Web application client IDs here, not Android client IDs. Sync Gradle and rebuild after changing either value.

## Google Cloud prerequisites

1. Configure the OAuth consent screen for the project.
2. Create or reuse a Web OAuth client for the Customer backend audience and place its ID in `GOOGLE_WEB_CLIENT_ID_CUSTOMER`.
3. Create or reuse a Web OAuth client for the Worker backend audience and place its ID in `GOOGLE_WEB_CLIENT_ID_WORKER`.
4. Create an Android OAuth client for package `com.snabbit.prototype.customer`.
5. Create another Android OAuth client for package `com.snabbit.prototype.worker`.
6. Register this debug SHA-1 for both Android clients: `CA:E1:DD:A9:C5:49:7F:68:D6:CA:98:60:00:27:9A:E7:22:16:59:C4`.
7. If a different debug/release signing key is used, run `./gradlew signingReport` from `android/` and register that certificate fingerprint too.
8. Add a Google account to the Google Play emulator from the login screen or Android Settings > Passwords, passkeys & accounts > Add account > Google.

## Emulator verification

Use a Google Play system image, then build from `android/`:

```powershell
.\gradlew.bat testCustomerDebugUnitTest testWorkerDebugUnitTest
.\gradlew.bat assembleCustomerDebug assembleWorkerDebug
```

Verified on the installed `Pixel_9_Pro` AVD:

- Customer and Worker launch successfully after a clean install.
- Both show flavor-specific Google login copy and **Continue with Google**.
- Neither login screen contains phone or OTP UI.
- With both local Web client IDs blank, both show `Google sign-in is not configured for this app.` and disable Continue.
- Worker shows **Add a Google account** in the blank-configuration state.
- Credential Manager and a real backend session could not be completed because no Web client ID was configured for this local build. After the two keys and Google Cloud clients above are configured, Continue becomes enabled and opens Credential Manager.

Final automated verification: 121 Customer tests and 121 Worker tests passed with zero failures; both debug APKs assembled.

## Gaps

- The backend session response and JWT do not expose a reliable shared-login marker. The Android **Switch worker** action must remain hidden until the backend provides one; guessing from the selected worker would be unsafe.
- Worker `select-profile` requires a fresh Google ID token, so selecting a worker can cause another Credential Manager interaction. Avoiding that requires a backend exchange based solely on the subject-bound selection token.
- Android has FCM registration and notification history but no WebSocket/SSE notification client. There is therefore no live stream to reconnect after refresh or close on logout.
- Notification snapshots do not provide a deep-link URI or enough structured targeting data for every destination.
- There is no shared repository cache-reset interface. Authenticated Navigation 3 stacks/ViewModels are discarded on logout, but adding explicit reset hooks would make future singleton caches safer.
- No approved Google G asset exists in the repository, so the button intentionally remains text-only.

## Progress and checkpoints

- `1c5d122` — audited Android/backend contracts and recorded gaps.
- `4fd0a68` — added Credential Manager dependencies, per-flavor configuration, and backup exclusions.
- `45dbd45` — added clean auth/session architecture, endpoints, encrypted store, worker picker, Koin and routes.
- `23155a7` — implemented Credential Manager login, nonce handling, flavor UI and error states.
- `915ca8c` — implemented rotating refresh, proactive refresh, logout/logout-all, Google profile and optional phone handling.
- `6459eff` — removed obsolete Customer/Worker phone OTP login and registration.
- `2ec3ecf` — added Google auth use-case and concurrent session-refresh tests.

## Uncommitted files intentionally left alone

The repository still contains unrelated work in booking/per-visit screens and repositories, wallet code, older tests, Gradle/local environment files, protected Android docs, generated output, and backend files. Those files were not staged by this goal. Run `git status --short` for the authoritative current list.

Copy the stable API summary into `docs/android/api-map.md` manually later; that mixed file was intentionally not edited here.
