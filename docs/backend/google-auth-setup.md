# Google authentication setup

This backend accepts Google ID tokens obtained by the two Android apps. It does not use a browser redirect, an authorization code, a Google client secret, or a Firebase service-account key for sign-in.

## Google Cloud checklist

1. Select or create the Google Cloud project used for Snabbit identity.
2. Open Google Auth Platform and configure Branding, Audience and Data Access. Request only the OpenID profile and email scopes needed for basic sign-in.
3. If the publishing status is **Testing**, add every person who must sign in under Audience > Test users. Google documents that test-mode apps are available only to listed test users.
4. Create an OAuth 2.0 client of type **Web application**. This is the server audience used when Android requests an ID token and when the backend validates `aud`. No redirect URI is needed for this native ID-token flow.
5. Create Android OAuth clients for package `com.snabbit.prototype.customer`: one for the debug signing SHA-1 and one for the release signing SHA-1.
6. Create Android OAuth clients for package `com.snabbit.prototype.worker`: one for the debug signing SHA-1 and one for the release signing SHA-1.
7. Obtain debug SHA-1 fingerprints from the debug keystore and release SHA-1 fingerprints from the actual release signing key or Play App Signing certificate. Do not reuse a debug fingerprint for release.
8. Configure each Android flavor to request an ID token for the Web application client ID. The backend audience lists contain Web/server client IDs, not Android client IDs.
9. Keep the customer and worker backend audience lists separate. A token accepted by the customer endpoint must not automatically be accepted by the worker endpoint.

Google's backend-auth guidance requires sending the ID token over HTTPS and verifying signature, audience, issuer and expiry; it also identifies `sub`, rather than email, as the stable account identity: <https://developers.google.com/identity/sign-in/android/backend-auth>.

## Environment variable names

Set these for Google verification and sessions:

- `GOOGLE_IDENTITY_VERIFIER`
- `GOOGLE_CUSTOMER_CLIENT_IDS`
- `GOOGLE_WORKER_CLIENT_IDS`
- `AUTH_ISSUER`
- `JWT_SECRET`
- `AUTH_REFRESH_IDLE_TTL`
- `AUTH_REFRESH_ABSOLUTE_TTL`
- `AUTH_REFRESH_GRACE_WINDOW`
- `AUTH_MAX_ACTIVE_SESSIONS`
- `AUTH_ACCOUNT_RATE_LIMIT`
- `AUTH_ACCOUNT_RATE_WINDOW`
- `AUTH_WORKER_SELECTION_TTL`

Optional local/development seed names:

- `DEV_SEED_ENABLED`
- `DEV_SEED_DEMO_CUSTOMER_EMAIL`
- `DEV_SEED_WORKER_LOGIN_EMAIL`

Both development email variables must be empty in production. Values belong only in the untracked local `.env` or the deployment secret/configuration store; never put them in source, documentation, images or logs.

Firebase configuration remains separate: Android `google-services.json` files and the FCM service account configure push notifications, not backend Google sign-in.

User Service still needs the existing `OTP_PROVIDER`, Twilio credential, Verify service SID, OTP TTL/limit and `OTP_PEPPER` configuration for staff phone authentication. Booking Service still needs its independent arrival-OTP provider configuration. Those values are not Google sign-in configuration and were intentionally retained.
