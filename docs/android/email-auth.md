# Customer email authentication for Android

## Scope

This document tracks the Customer-flavor Android integration for backend email/password authentication. The Worker flavor keeps its existing Google sign-in and worker picker. Backend code and existing mixed Android documentation are read-only for this work.

## What was built

- The Customer auth route now has **Login** and **Sign Up** tabs beside the existing Google action. Email/password remains usable when the Google Web client ID is absent; only the Google action is disabled in that state.
- Login supports email, password visibility, inline validation, invalid-credential handling, verification routing, forgot-password routing, and best-effort Credential Manager password saving. Credential saving is emitted after navigation and is time-bounded, so Google Password Manager cannot block entry to Home.
- Sign Up supports full name, normalized email, a live password-policy checklist, terms acceptance, server field errors, and a verification hand-off that carries only the email in navigation.
- Verification supports a six-digit numeric/pasteable code, automatic submit on digit six, resend cooldown, rate-limit countdown, change email, session persistence, and best-effort password saving.
- Forgot/reset supports enumeration-safe reset requests, code plus a new password, local policy feedback, session clearing after reset, and the post-reset message **Password updated. Please log in.**
- Customer Profile calls `/me` and shows **Change password** only when `hasPassword` is true. A successful change stores the returned rotating token pair and keeps the current device signed in; logout and logout-all remain available.
- `SESSION_REVOKED` clears the session and publishes a one-shot sign-in notice. Existing encrypted session storage, backup exclusion, OkHttp authentication, single-flight refresh, and silent start-destination refresh are reused.
- The pure Kotlin domain layer contains email/name/password validators plus register, verify, resend, password-login, forgot, reset, and change-password use cases. Retrofit DTOs/mappers and the repository are separated from Compose.
- `VerifyEmailRoute`, `ForgotPasswordRoute`, and `ChangePasswordRoute` use Navigation 3 and never carry passwords, codes, or tokens. ViewModels clear secret fields after terminal actions and when their screen-owned store is cleared.
- The Worker flavor remains Google-only and retains its worker picker.

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
- General backend rate limits count down from `retryAfterSeconds`; resend also gates the action with its own countdown.

## Gaps

- The backend does not return the resend cooldown in a successful `202`; Android must use the documented/configured default of 60 seconds and replace it with `retryAfterSeconds` when the backend rate-limits.
- The backend's complete bundled common-password corpus is not an API contract. Android can reject a conservative local subset for immediate feedback, but only the backend can provide authoritative common-password rejection.
- Existing terms/privacy text has no typed in-app destination or configured URL. The checkbox and accessible links can be presented using the existing localized legal copy, but opening a canonical legal document needs a future product-provided destination.
- The existing launch rule preserves a stored session when refresh fails offline. This matches current offline behavior, but it cannot prove server validity until connectivity returns.

## Progress

- Audit and contract checkpoint: `717910a`.
- Domain/data checkpoint: `9cc8b9b`.
- Customer presentation, Profile, navigation, session notice, and tests: `d339117`.
- Credential Manager non-blocking runtime fix: `f4aa274`.
- Exit-secret test and live rate-limit countdown: `3003dd2`.

## Local Mailpit verification

1. Start the local backend and confirm the gateway health endpoint is `UP`; Mailpit is available at `http://localhost:8025`.
2. Install `app-customer-debug.apk` on a Google Play emulator and use generated placeholder credentials only.
3. Sign up. Open Mailpit on the PC, copy the newest verification code for that generated address, enter it, and verify that Home opens.
4. Log out. Submit a wrong password and confirm the friendly generic error and cleared password field; then log in with the correct password.
5. Log out, request a reset, copy the newest reset code from Mailpit, set a replacement password, and confirm the Login screen. Log in with the replacement password.
6. In Profile, confirm **Change password** is present for this password account, change it, and verify the app remains signed in.
7. Install `app-worker-debug.apk`; confirm it still shows **Continue with Google** and does not show the email Login/Sign Up tabs.

This sequence was completed on the Pixel 9 Pro Google Play emulator against the local backend. Observed responses were register `202`, verify `200`, wrong login `401`, correct login `200`, forgot `202`, reset `204`, `/me` `200`, logout `204`, and change password `200`. The app reached Home after verification and both successful logins. The password-save provider UI also appeared; cancelling it did not block authentication. No app crash occurred. A Google sign-in itself was not exercised because no Google Web client ID/account was configured, and cross-device session revocation was covered by unit tests rather than a second physical device.

## Verification gates

- `testCustomerDebugUnitTest`: 138 tests, 0 failures, 0 errors, 0 skipped.
- `testWorkerDebugUnitTest`: 138 tests, 0 failures, 0 errors, 0 skipped.
- `assembleCustomerDebug` and `assembleWorkerDebug` pass.
- Tests cover validators, use-case delegation/failures, complete backend error mapping, ViewModel tab/validation/double-submit/resend/auto-submit/secret-clearing behavior, session persistence/reset, start destination, `/me` password capability, session revocation, single-flight refresh, and a source check against direct sensitive auth logging.
- No backend source was edited for this goal. Protected mixed documentation, local configuration, Firebase files, and unrelated worktree changes were not staged.

## Uncommitted files intentionally preserved

The following pre-existing work remains outside these commits:

- Android booking/per-visit and wallet work in `data/booking`, `domain/booking`, `ui/booking`, `data/wallet`, `domain/wallet`, `CustomerNavigation.kt`, `Routes.kt`, `RoutesTest.kt`, and related tests.
- Unrelated shared-file hunks still present in `AppModules.kt`, `SnabbitApp.kt`, and `strings.xml`; only email-auth hunks from those mixed files were committed.
- Existing network/repository-test work including `NetworkClientFactory.kt`, `UnsupportedTokenRefresher.kt`, and the untracked repository test directories/files.
- Protected mixed docs: `PROGRESS.md`, `README.md`, `api-gaps.md`, `api-map.md`, `assets-todo.md`, and `ui-spec.md`.
- Repository/local/generated work including root and Android Gradle settings, `compose.local-codex.yaml`, `local.properties`, `output/`, `tmp/`, rendered docs, the handoff file, and the existing Wallet Service edits.

These changes were left untouched and unstaged. No `.env`, secret, `google-services.json`, or `local.properties` value was read into source, tests, docs, or commits.
