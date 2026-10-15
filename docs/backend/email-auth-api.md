# Customer email authentication API

## Conventions

All routes are under `/api/v1/auth`. Email identity is trimmed and lowercased, limited to 254 characters, and compared case-insensitively. Phone is not required. Passwords and codes are never returned or logged.

Successful session-producing endpoints return the same rotating-session payload used by Google sign-in:

```json
{
  "accessToken": "$ACCESS_TOKEN",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "$REFRESH_TOKEN",
  "user": {
    "id": "$CUSTOMER_ID",
    "name": "$DISPLAY_NAME",
    "email": "$EMAIL",
    "pictureUrl": null,
    "isNewUser": false,
    "emailVerified": true,
    "hasPassword": true,
    "authProviders": ["PASSWORD"],
    "role": "CUSTOMER"
  }
}
```

`authProviders` may contain `GOOGLE`, `PASSWORD`, or both. Existing Google sign-in, refresh, logout, and logout-all contracts are unchanged.

Generic accepted operations return HTTP `202` and:

```json
{"status":"REQUEST_ACCEPTED"}
```

This body is identical whether or not the email already exists.

## Register

`POST /api/v1/auth/register` is public.

```json
{
  "name": "$DISPLAY_NAME",
  "email": "$EMAIL",
  "password": "$PASSWORD",
  "termsAccepted": true,
  "deviceId": "$OPTIONAL_DEVICE_ID",
  "deviceName": "$OPTIONAL_DEVICE_NAME"
}
```

Returns the generic `202` body and never issues a session. A new account remains unverified until its code is accepted. A verified existing account receives an informational email; no duplicate is created. A pre-provisioned development customer is attached by verified email so its Plus and favourite-worker state remains on the same customer ID.

Errors: `WEAK_PASSWORD` with `reason`, `TERMS_NOT_ACCEPTED`, `VALIDATION_ERROR`, or `RATE_LIMITED`.

## Verify email

`POST /api/v1/auth/verify-email` is public.

```json
{
  "email": "$EMAIL",
  "code": "$SIX_DIGIT_CODE",
  "deviceId": "$OPTIONAL_DEVICE_ID",
  "deviceName": "$OPTIONAL_DEVICE_NAME"
}
```

Success returns HTTP `200` and the normal session payload with `isNewUser: true`. The code is single-use. Errors: `INVALID_OR_EXPIRED_CODE`, `TOO_MANY_ATTEMPTS`, `VALIDATION_ERROR`, or `RATE_LIMITED`.

## Resend verification

`POST /api/v1/auth/resend-verification` is public.

```json
{"email":"$EMAIL"}
```

Returns the generic `202` body. A new code invalidates the previous code. The default resend cooldown is 60 seconds and the default cap is five sends per account per hour. Errors do not reveal account existence; `RATE_LIMITED` includes `retryAfterSeconds`.

## Login

`POST /api/v1/auth/login` is public.

```json
{
  "email": "$EMAIL",
  "password": "$PASSWORD",
  "deviceId": "$OPTIONAL_DEVICE_ID",
  "deviceName": "$OPTIONAL_DEVICE_NAME"
}
```

Success returns HTTP `200` and the normal session payload with `isNewUser: false`. Unknown email and wrong password both return `INVALID_CREDENTIALS`. Only a correct password can produce `EMAIL_NOT_VERIFIED` or `ACCOUNT_DISABLED`. An unverified account receives a replacement verification code when cooldown rules permit.

## Forgot password

`POST /api/v1/auth/forgot-password` is public.

```json
{"email":"$EMAIL"}
```

Always returns the generic `202` body. A verified password or Google-only customer receives a reset code. Unknown or unusable accounts receive the same response.

## Reset password

`POST /api/v1/auth/reset-password` is public.

```json
{
  "email": "$EMAIL",
  "code": "$SIX_DIGIT_CODE",
  "newPassword": "$NEW_PASSWORD"
}
```

Success returns HTTP `204` with no tokens. Every existing customer session is revoked and old refresh tokens return `SESSION_REVOKED`; the customer must log in. A Google-only account gains the `PASSWORD` provider. Errors: `WEAK_PASSWORD`, `INVALID_OR_EXPIRED_CODE`, `TOO_MANY_ATTEMPTS`, `VALIDATION_ERROR`, or `RATE_LIMITED`.

## Change password

`POST /api/v1/auth/change-password` requires a valid `CUSTOMER` access token.

```json
{
  "currentPassword": "$CURRENT_PASSWORD",
  "newPassword": "$NEW_PASSWORD"
}
```

Success returns HTTP `200` with a fresh access and refresh token pair. The caller session remains active with its refresh credential rotated; every other customer session is revoked. A Google-only account receives `PASSWORD_NOT_SET` and must use forgot/reset password. Other errors: `INVALID_CREDENTIALS`, `WEAK_PASSWORD`, or `SESSION_REVOKED`.

## Current customer

`GET /api/v1/auth/me` requires a valid `CUSTOMER` access token and returns the `user` object shown in the session payload. It includes `emailVerified`, `hasPassword`, and `authProviders`; it never includes password hashes or challenge data.

## Error shape

Errors use `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "$HTTP_TITLE",
  "status": 429,
  "detail": "$GENERIC_MESSAGE",
  "instance": "/api/v1/auth/$OPERATION",
  "code": "RATE_LIMITED",
  "retryAfterSeconds": 30
}
```

Supported authentication codes are `INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `WEAK_PASSWORD`, `INVALID_OR_EXPIRED_CODE`, `TOO_MANY_ATTEMPTS`, `RATE_LIMITED`, `TERMS_NOT_ACCEPTED`, `ACCOUNT_DISABLED`, `PASSWORD_NOT_SET`, `VALIDATION_ERROR`, `SESSION_REVOKED`, and the existing Google/session codes. `VALIDATION_ERROR` includes a `fields` object; `WEAK_PASSWORD` includes a stable `reason`.

## Account linking rules

- Google verification for an unverified password registration links the customer, marks email verified, deletes the password hash, and removes `PASSWORD`; this prevents a pre-registered attacker password from surviving.
- Google verification for an already verified password customer preserves the password and records both providers.
- A Google-only customer proves email ownership through forgot/reset password before gaining `PASSWORD`.
- Customer email endpoints never create or authenticate workers or staff. Worker sign-in remains Google-based. Staff phone OTP remains unchanged.

## Notifications and deep links

Authentication email is delivered directly by User Service through `AuthEmailSender`; codes never enter Kafka, transactional outbox events, notifications, analytics, or application logs. These plain emails contain only the code and an instruction to ignore the message when the recipient did not initiate it. There is no authentication deep link in this flow.

## How to test locally with Mailpit

1. Start the infrastructure Compose file; Mailpit binds SMTP to `127.0.0.1:1025` and its web UI to `127.0.0.1:8025`.
2. When User Service runs in Docker, its local default SMTP host is `mailpit`. When it runs directly on the host, set `SMTP_HOST=localhost` and `SMTP_PORT=1025`.
3. Set a local-only `AUTH_CODE_HMAC_SECRET` outside source control, start User Service and API Gateway, then call register or forgot-password using placeholder test credentials.
4. Open `http://localhost:8025`, select the message, and copy the six-digit code into verify-email or reset-password.
5. Confirm no code appears in HTTP responses, service logs, Kafka, or outbox rows.

Mailpit is local-only. Production startup rejects Mailpit/local SMTP and requires real SMTP configuration plus a separate HMAC secret.
