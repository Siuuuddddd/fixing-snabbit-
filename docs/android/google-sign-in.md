# Android Google sign-in

## Current state

- Both product flavors use the same Compose `LoginScreen` and `LoginViewModel`. The current flow is phone entry, OTP request/verification, and a customer-only display-name registration step. `AuthRepository`, `DefaultAuthRepository`, `AuthApi`, the remote data source, DTOs, strings, and unit tests are all OTP-shaped.
- The intro onboarding is a separate marketing flow backed by `OnboardingLocalDataSource`; it is not customer registration. Splash asks `LaunchRepository` for `ONBOARDING`, `LOGIN`, or `AUTHENTICATED`, but currently treats any stored access token as authenticated and does not refresh it first.
- `EncryptedTokenStore` uses `EncryptedSharedPreferences` (`snabbit_secure_auth`) and stores only an access token. It has no refresh token, expiry, session profile, or observable session state. `DefaultSessionManager` clears that store and emits an in-memory signed-out event.
- `AuthInterceptor` attaches `Authorization: Bearer` to non-auth requests. `RefreshingAuthenticator` delegates to `TokenRefresher`, but Koin currently binds the placeholder `UnsupportedTokenRefresher`, which clears the local session on any refresh attempt. The pre-existing `NetworkClientFactory` builds the shared OkHttp client with this interceptor, authenticator, and redacted BASIC logging.
- Notification history and FCM device registration are implemented. `SnabbitApp` registers the current FCM installation after authentication; `DefaultProfileRepository.signOut` tries to unregister it before clearing the local session. There is no Android WebSocket or SSE notification-stream implementation, despite the backend exposing live WebSockets.
- Authentication navigation uses Navigation 3 keys `SplashRoute`, `OnboardingRoute`, and `LoginOtpRoute`. Successful login flips top-level in-memory authentication state and opens the customer shell or worker shell. A `SessionManager.signedOut` event returns to the authentication flow. Koin owns storage, networking, repositories, ViewModels, and Navigation 3 entries in the existing module graph.
- The profile screen derives the current UUID and role from JWT claims, loads customer address/household/Plus summaries, and offers local sign-out. It does not show Google identity, save an optional phone, call backend logout/logout-all, clear Credential Manager state, or offer worker profile switching.
- There is no Credential Manager dependency, Google ID-token provider, rotating session store, worker picker screen, proactive refresh scheduler, or session-scoped cache reset abstraction.

## Confirmed backend contract

- Customer sign-in: `POST /api/v1/auth/google`.
- Worker sign-in: `POST /api/v1/worker-auth/google`.
- Both accept `idToken`, optional `nonce`, optional `deviceId`, and optional `deviceName`.
- A session response contains `accessToken`, `tokenType`, `expiresIn`, `refreshToken`, and `user`. Customer user data contains `id`, `name`, `email`, nullable `pictureUrl`, `isNewUser`, and role. A normal worker response has the same session shape and `requiresWorkerSelection=false`.
- Shared development worker sign-in can instead return HTTP 200 with code `WORKER_SELECTION_REQUIRED`, `selectionToken`, `expiresIn`, exactly the eligible workers (`workerId`, first `name`, and `tier`), and `requiresWorkerSelection=true`.
- Worker selection: `POST /api/v1/worker-auth/select-profile`; it requires a fresh Google `idToken`, optional `nonce`, the `selectionToken`, `workerId`, and optional device metadata.
- Worker switching: authenticated `POST /api/v1/worker-auth/switch-profile` with `workerId`.
- Refresh: `POST /api/v1/auth/refresh` or `POST /api/v1/worker-auth/refresh` with the current `refreshToken`; success returns a complete session and replacement refresh token.
- Logout and logout-all: authenticated `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all`, and worker equivalents under `/api/v1/worker-auth`; success is HTTP 204.
- Optional customer phone: authenticated `PUT /api/v1/users/{userId}/phone` with `phoneNumber`. The backend accepts an Indian ten-digit mobile beginning with 6-9 and normalizes it to E.164.
- Errors use RFC 9457 `ProblemDetail` and the `code` property. Documented codes are `INVALID_GOOGLE_TOKEN`, `EMAIL_NOT_VERIFIED`, `WORKER_NOT_REGISTERED`, `WORKER_SELECTION_REQUIRED`, `INVALID_SELECTION_TOKEN`, `ACCOUNT_DISABLED`, `SESSION_REVOKED`, `REFRESH_IN_PROGRESS`, `RATE_LIMITED`, and `PHONE_REQUIRED`. The switch endpoint can also return `SHARED_WORKER_SESSION_REQUIRED`, and account linking can return `ACCOUNT_LINK_CONFLICT`.

## Gaps

- `WorkerSessionResponse` does not expose `sharedLogin`, and the access JWT has no shared-login claim. The app therefore cannot reliably show **Switch worker only for sessions marked shared-login** after selection, refresh, or process restart. The backend must expose a boolean or equivalent claim before the client can meet that condition without guessing.
- The backend requires a fresh Google ID token for `select-profile`. The app must reacquire it through Credential Manager after the user chooses a worker; it must not persist the earlier Google ID token. If this extra account prompt is not desired, the backend contract needs a selection exchange that relies only on its subject-bound selection token.
- Android has no client for `/api/v1/notifications/live` or `/api/v1/worker/notifications/live`, so there is no existing live stream to reconnect after token refresh or close on logout. This goal can preserve FCM registration/history and must not claim WebSocket reconnection until a stream data source is added.
- Notification snapshots have no deep-link URI or structured target, so the app cannot infer a booking identifier from a push or live event.
- The API provides Google user data only in session responses and has no documented current-session profile endpoint. To display name, email, and picture after restart, non-secret identity metadata must be retained with the encrypted session.
- No shared cache-reset contract currently exists. Repositories and ViewModels that retain user data will need an explicit session-bound reset mechanism or recreation of the authenticated navigation graph.
- An official Google G asset is not present in the Android resources. The login button will remain text-only unless an approved branding asset is supplied.

## Progress

- Step 0: audited the existing Android authentication, session, navigation, notification, profile, and Koin paths; verified the implemented backend controllers/DTOs against the backend Google-auth documentation; recorded the exact API contract and unresolved gaps above.
