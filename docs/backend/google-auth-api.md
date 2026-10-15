# Google authentication API

All bodies are JSON. Send requests over HTTPS. Access tokens use `Authorization: Bearer <access token>`. Never send a Google ID token as a bearer token and never persist it as the Snabbit session token.

## Customer sign-in

`POST /api/v1/auth/google` is public.

Request:

```json
{
  "idToken": "<Google ID token>",
  "nonce": "<optional nonce used by the client request>",
  "deviceId": "<optional app installation identifier>",
  "deviceName": "<optional user-facing device label>"
}
```

Success:

```json
{
  "accessToken": "<Snabbit access JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "<opaque rotating refresh token>",
  "user": {
    "id": "<customer UUID>",
    "name": "<verified profile name>",
    "email": "<verified profile email>",
    "pictureUrl": "<verified picture URL or null>",
    "isNewUser": true,
    "role": "CUSTOMER"
  }
}
```

The first valid sign-in creates an ACTIVE customer unless a matching unlinked pre-provisioned email profile exists. Existing phone-only profiles are not linked by phone. A Google-only account has no Snabbit password; a Google password change does not notify Snabbit or invalidate Snabbit sessions.

The former customer `POST /api/v1/auth/otp/request`, `POST /api/v1/auth/otp/verify`, and `POST /api/v1/auth/register` endpoints have been removed. They are not compatibility aliases for Google sign-in.

## Worker sign-in and picker

`POST /api/v1/worker-auth/google` is public and accepts the same four request fields as customer sign-in. A normal active pre-provisioned worker receives the session response above with role `WORKER`, `isNewUser=false`, and the worker UUID.

The configured development shared identity receives no session initially:

```json
{
  "code": "WORKER_SELECTION_REQUIRED",
  "selectionToken": "<opaque single-use token>",
  "expiresIn": 300,
  "workers": [
    { "workerId": "<worker UUID>", "name": "<first name>", "tier": "STANDARD" }
  ],
  "requiresWorkerSelection": true
}
```

The list contains exactly the ten stable active seeded workers and exposes no phone number. A real non-seeded worker with the same verified email takes the normal path and never appears in this list.

`POST /api/v1/worker-auth/select-profile` is public.

```json
{
  "idToken": "<fresh Google ID token for the same identity>",
  "nonce": "<optional nonce>",
  "selectionToken": "<picker token>",
  "workerId": "<selected seeded worker UUID>",
  "deviceId": "<optional app installation identifier>",
  "deviceName": "<optional device label>"
}
```

It consumes the token once and returns a worker session. The selection token is valid for the configured short lifetime, stored only as a hash, and bound to the verified Google `sub`.

`POST /api/v1/worker-auth/switch-profile` requires a worker access token from a shared-login session:

```json
{ "workerId": "<another eligible seeded worker UUID>" }
```

The current session is revoked immediately and a new worker session is returned for the selected profile. Normal worker sessions receive `SHARED_WORKER_SESSION_REQUIRED`.

The former worker `POST /api/v1/worker-auth/otp/request` and `POST /api/v1/worker-auth/otp/verify` endpoints have been removed.

## Refresh and logout

Customer refresh: `POST /api/v1/auth/refresh`.

Worker refresh: `POST /api/v1/worker-auth/refresh`.

Both are public and accept:

```json
{ "refreshToken": "<current opaque refresh token>" }
```

Success returns a complete session response and a replacement refresh token. The submitted token must be discarded. Two near-simultaneous uses have one winner; the loser receives retryable `REFRESH_IN_PROGRESS`. Reuse after the grace window revokes the token family and returns `SESSION_REVOKED`.

Customer logout endpoints are `POST /api/v1/auth/logout` and `POST /api/v1/auth/logout-all`. Worker equivalents are under `/api/v1/worker-auth`. They require the correct role and access token and return `204 No Content`. Logout revokes the current `sid`; logout-all revokes every active session and advances the account session cutoff.

Logout does not currently deactivate a push registration automatically. Before logout, the app should call `DELETE /api/v1/notifications/devices/{deviceId}` or the worker equivalent when it wants to deactivate that installation's FCM token.

## Optional customer phone

`PUT /api/v1/users/{userId}/phone` requires that customer's access token.

```json
{ "phoneNumber": "<Indian mobile number>" }
```

It accepts a ten-digit Indian mobile beginning with 6–9 and normalizes it to E.164. This field is unverified and is not an authentication identity. SELF home-contact checkout returns HTTP 409 with `PHONE_REQUIRED` when it is absent.

## Errors

Errors use RFC 9457/Spring `ProblemDetail` JSON with `status`, `detail` and a `code` property. Relevant codes are:

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `INVALID_GOOGLE_TOKEN` | Signature, issuer, audience, expiry, subject or nonce validation failed. |
| 401 | `EMAIL_NOT_VERIFIED` | Google did not attest the email. |
| 403 | `WORKER_NOT_REGISTERED` | No eligible pre-provisioned worker can sign in; no account detail is disclosed. |
| 200 | `WORKER_SELECTION_REQUIRED` | Picker response with `requiresWorkerSelection=true`; not an error status. |
| 401 | `INVALID_SELECTION_TOKEN` | Picker token is invalid, expired, consumed, subject-mismatched, or the worker is ineligible. |
| 403 | `ACCOUNT_DISABLED` | The customer account is not active. |
| 401 | `SESSION_REVOKED` | Session expired, was logged out, exceeded the cap, or its refresh family was revoked. |
| 409 | `REFRESH_IN_PROGRESS` | A concurrent refresh already rotated this token; `retryable=true`. |
| 429 | `RATE_LIMITED` | Gateway IP or service account rate limit was exceeded. |
| 409 | `PHONE_REQUIRED` | SELF home contact needs a saved customer phone. |

Messages remain generic. Clients must branch on `code`, not parse `detail`.

## Staff authentication retained

Staff authentication is outside this migration and still uses the shared phone-verification provider at `POST /api/v1/staff-auth/otp/request` and `POST /api/v1/staff-auth/otp/verify`. Its provider and environment configuration remain required by User Service. Customer and worker apps must not call these staff-only endpoints.

## Notifications, WebSocket and deep links

The new access JWT keeps the existing UUID subject and role. Customer notification history, read state and device registration remain under `/api/v1/notifications/**`; worker equivalents remain under `/api/v1/worker/notifications/**`. Live streams remain WebSockets at `/api/v1/notifications/live` and `/api/v1/worker/notifications/live`. The gateway validates the JWT and injects the trusted customer or worker identity header used by Notification Service.

`NotificationSnapshot` currently contains `notificationId`, `eventType`, `title`, `body`, `critical`, `createdAt`, and `readAt`. It does not contain a deep-link URI or structured target. Until that contract is extended, the client may route known `eventType` values to an appropriate top-level screen, but must not infer a booking identifier that the payload does not provide. This is recorded as a gap in `google-auth.md`.

At the end of this backend goal, copy this API summary into `docs/android/api-map.md` manually; that mixed-edit file is intentionally not modified here.
