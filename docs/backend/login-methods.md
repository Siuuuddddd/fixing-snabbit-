# Customer login methods

## Progress

- Customer phone OTP is available through Twilio Verify for Indian mobile numbers.
- Existing Google and email/password authentication remain available.
- All three methods issue the same JWT and opaque rotating refresh-session response.
- `rememberMe=true` is the default. Remembered sessions use a sliding 180-day idle window (and the optional configured absolute cap); unremembered sessions have a configurable 24-hour absolute cap.
- Worker Google authentication and staff phone OTP are unchanged.

## API

### Request a phone code

`POST /api/v1/auth/otp/request`

```json
{ "phoneNumber": "+919876543210" }
```

Successful requests always return HTTP `202` with the same shape, whether or not an account already exists:

```json
{ "status": "REQUEST_ACCEPTED", "retryAfterSeconds": 30 }
```

Only Indian mobile numbers are accepted: ten national digits beginning with 6–9, optionally supplied with `+91`, `91`, or a leading `0`. The service enforces a 30-second resend cooldown, five sends per hour per number, and per-IP throttling. The provider code is never returned or logged.

### Verify a phone code

`POST /api/v1/auth/otp/verify`

```json
{
  "phoneNumber": "+919876543210",
  "code": "123456",
  "name": "Asha",
  "rememberMe": true,
  "deviceId": "optional-installation-id",
  "deviceName": "optional-device-name"
}
```

`name` is optional. A successful first verification creates an ACTIVE phone-only customer; a later verification retrieves that customer. Five wrong codes burn the challenge. Success returns the standard customer session response with `isNewUser`, nullable `email`, and nullable `phoneNumber`.

### Google and email session options

The Google request (`POST /api/v1/auth/google`), email login (`POST /api/v1/auth/login`), and email verification (`POST /api/v1/auth/verify-email`) accept `rememberMe`, defaulting to `true` for older clients.

Common authentication error codes are `VALIDATION_ERROR`, `INVALID_OR_EXPIRED_CODE`, `TOO_MANY_ATTEMPTS`, `RATE_LIMITED`, `ACCOUNT_DISABLED`, and `OTP_PROVIDER_UNAVAILABLE`. Rate-limited responses include `retryAfterSeconds` when known.

## Identity rules

- Phone accounts are looked up only by their verified E.164 phone number.
- Google and email/password accounts are looked up by verified email identity.
- The login endpoints do not automatically merge a phone account with an email/Google account.
- Refresh-token plaintext exists only on the client; User Service stores its SHA-256 hash in the session table.

For local demo data only, `DEV_SEED_DEMO_CUSTOMER_PHONE` may attach the first successfully verified matching phone to the pre-provisioned `DEV_SEED_DEMO_CUSTOMER_EMAIL` customer. Both values require `DEV_SEED_ENABLED=true`; production rejects them.

## Provider setup

Configure User Service with `OTP_PROVIDER=twilio-verify`, `TWILIO_ACCOUNT_SID`, either `TWILIO_AUTH_TOKEN` or the API-key pair, and `TWILIO_VERIFY_SERVICE_SID`. The same provider bean continues to serve staff authentication without changing staff routes. See `.env.example` for throttle and session variables. Local `logging` OTP is test-only and is rejected by the production guard.

## Gaps

- Automatic cross-linking between independently created phone and email/Google accounts is intentionally not implemented. A future authenticated, re-verification-based account-linking flow is required.
- Staff authentication still uses phone OTP and needs its own replacement if the product later standardizes staff on another identity provider.
- Copy this API summary into `docs/android/api-map.md` manually after its unrelated in-progress edits are resolved.
