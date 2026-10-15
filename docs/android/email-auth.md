# Customer email authentication for Android

## Scope

This document tracks the Customer-flavor Android integration for backend email/password authentication. The Worker flavor keeps its existing Google sign-in and worker picker. Backend code and existing mixed Android documentation are read-only for this work.

## Audit

### Existing authentication UI and state

- `LoginScreen` is a shared Compose/Material 3 screen that currently contains only the brand header, **Continue with Google**, Google configuration/error text, **Add a Google account**, and terms/privacy copy.
- `LoginViewModel` owns Google loading/error state and emits `Authenticated`, `OpenWorkerPicker`, and `AddGoogleAccount`. It has no email form state today.
- `AuthenticationFlow` in `SnabbitApp` uses Navigation 3 with a private stack beginning at `SplashRoute`. `LoginRoute` is shared; authenticated customer/worker shells are outside this stack. Successful authentication clears the auth flow by switching the root to the authenticated shell.
- `DefaultLaunchRepository` checks the encrypted stored session, performs a silent single-flight refresh through `TokenRefresher`, and opens the authenticated shell when refresh succeeds. A revoked/non-retryable session is cleared by `SessionManager`; offline refresh preserves the stored session under the existing behavior.

### Existing data and domain layers

- `AuthApi` is the Retrofit interface for Google sign-in, refresh, logout/logout-all, worker selection, and customer phone updates.
- `RetrofitAuthRemoteDataSource` selects customer or worker endpoints by `AppRole` and maps the worker-picker response separately.
- `DefaultAuthRepository` maps backend `ProblemDetail` responses to `AuthFailureReason`, converts `SessionResponseDto`, and saves successful sessions through `SessionStore`.
- `EncryptedSessionStore` persists the access token, rotating refresh token, expiry, and non-secret user metadata in encrypted preferences. The secure preference file is already excluded from backup/device transfer.
- `SessionTokenRefresher` serializes refresh through a `Mutex`, retries `REFRESH_IN_PROGRESS`, saves the rotated session before retrying the failed request, preserves state on network failure, and signs out for `SESSION_REVOKED` or other terminal client failures.
- `AuthInterceptor` skips authentication for current public Google/refresh routes. The new public email endpoints must be added to that allow-list so stale bearer state is never attached.
- `AuthUseCases.kt` currently contains Google, worker selection/switching, refresh, logout, and session-observation use cases. Email-auth use cases and pure validators do not yet exist.

### Navigation and dependency injection

- Typed keys currently include `LoginRoute` and `WorkerPickerRoute`. New customer-only keys are required for verification and forgot/reset flows; they may carry an email but never a password or code.
- `AppModules` provides one `SessionStore`, `AuthRemoteDataSource`, `AuthRepository`, `SessionManager`, refresh stack, flavor `AppRole`, and parameterized Google provider/ViewModels. New repositories/use cases/ViewModels will reuse these bindings.
- The customer Profile screen is registered through the shared Navigation 3 entry provider. `ProfileViewModel` reads `ProfileSummary` and supports logout/logout-all. It does not call `/auth/me`, and `ProfileSummary` has no `hasPassword` field yet.

## Backend contract used

All routes are under `/api/v1/auth`.

| Purpose | Method and path | Request | Success |
| --- | --- | --- | --- |
| Register | `POST /register` | `name`, `email`, `password`, `termsAccepted`, optional `deviceId`, `deviceName` | `202 {"status":"REQUEST_ACCEPTED"}` |
| Verify email | `POST /verify-email` | `email`, six-digit `code`, optional device fields | `200` session |
| Resend verification | `POST /resend-verification` | `email` | generic `202` |
| Password login | `POST /login` | `email`, `password`, optional device fields | `200` session |
| Forgot password | `POST /forgot-password` | `email` | generic `202` |
| Reset password | `POST /reset-password` | `email`, `code`, `newPassword` | `204`, all sessions revoked |
| Change password | `POST /change-password` | bearer token plus `currentPassword`, `newPassword` | `200` fresh session; other sessions revoked |
| Current customer | `GET /me` | bearer token | user with `emailVerified`, `hasPassword`, `authProviders` |

The session is the existing `accessToken`, `tokenType`, `expiresIn`, `refreshToken`, and `user`; the user now also supplies `emailVerified`, `hasPassword`, and `authProviders`. Verification and password login must save this session exactly like Google sign-in.

Backend error codes used by the app are `INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `WEAK_PASSWORD`, `INVALID_OR_EXPIRED_CODE`, `TOO_MANY_ATTEMPTS`, `RATE_LIMITED`, `TERMS_NOT_ACCEPTED`, `ACCOUNT_DISABLED`, `PASSWORD_NOT_SET`, `VALIDATION_ERROR`, and `SESSION_REVOKED`. `RATE_LIMITED` may include `retryAfterSeconds`; `VALIDATION_ERROR` may include field errors; `WEAK_PASSWORD` may include a stable reason.

## Decisions

- Email identity is trimmed and lowercased before requests. Validation permits at most 254 characters and does not rewrite provider aliases.
- Password validation mirrors the documented backend policy: 8–128 characters, not equal to normalized email or name, and not in the app's bundled common-password set. The backend remains authoritative and may return a more specific `WEAK_PASSWORD` reason.
- Passwords and codes exist only in ViewModel memory, are never route arguments or saveable state, and are cleared on terminal navigation and errors that require re-entry.
- Customer email forms remain enabled when the Google Web client ID is blank. Only the Google action shows its configuration state.
- Register, resend, and forgot-password keep enumeration-safe generic success copy.
- Credential Manager password saving is best-effort after a successful password login or completed registration verification; failure never blocks sign-in.

## Gaps

- The backend does not return the resend cooldown in a successful `202`; Android must use the documented/configured default of 60 seconds and replace it with `retryAfterSeconds` when the backend rate-limits.
- The backend's complete bundled common-password corpus is not an API contract. Android can reject a conservative local subset for immediate feedback, but only the backend can provide authoritative common-password rejection.
- Existing terms/privacy text has no typed in-app destination or configured URL. The checkbox and accessible links can be presented using the existing localized legal copy, but opening a canonical legal document needs a future product-provided destination.
- The existing launch rule preserves a stored session when refresh fails offline. This matches current offline behavior, but it cannot prove server validity until connectivity returns.

## Progress

- Step 0 complete: Android rules/UI spec, Google-auth implementation, backend endpoint documentation, controllers, DTOs, session lifecycle, refresh behavior, navigation, profile, and Koin bindings audited.

## Local Mailpit verification plan

Run the backend and Mailpit, install the Customer debug APK on the Google Play Pixel 9 Pro emulator, and use only generated placeholder credentials. Register, read the six-digit code from `http://localhost:8025`, verify and reach Home; then exercise logout/login, invalid password, forgot/reset, login with the replacement password, and authenticated change-password. The Worker APK must retain the existing Google-only flow.

## Uncommitted files intentionally preserved

The worktree already contains unrelated Android booking/per-visit, repository-test, network, Google-auth/Gradle, protected-doc, Wallet, local-environment, and generated-output changes. They remain authoritative user work and are not part of this feature unless an unavoidable shared-file hunk is explicitly isolated.
