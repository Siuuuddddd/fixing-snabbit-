# Google authentication and session migration audit

## Scope and status

This document began as the Step 0 audit of the authentication implementation before the Google ID-token and rotating-session work. The audit sections retain that historical context; the Progress and Gaps sections record the completed migration and current state.

The migration is backend-only. Android changes, real identity values, local secrets, and the untracked `docs/android/conversation-handoff.md` are outside this work.

## Pre-migration authentication ownership and routes

Customer authentication is owned by User Service:

- `POST /api/v1/auth/otp/request` starts phone verification.
- `POST /api/v1/auth/otp/verify` verifies the six-digit code.
- `POST /api/v1/auth/register` consumes a one-time registration grant and creates a customer when the verified phone has no profile.

Worker authentication is owned by Worker Service:

- `POST /api/v1/worker-auth/otp/request` starts phone verification.
- `POST /api/v1/worker-auth/otp/verify` verifies the code and requires an existing active worker profile. Workers are not created by login.

Staff authentication is also phone-OTP based and is owned by User Service under `/api/v1/staff-auth/**`. This is an important shared dependency: `StaffAuthenticationService` uses the same platform `PhoneVerificationProvider`, Twilio Verify implementation, Redis challenge store, and `AuthProperties`. Step 6 preserved this provider, its User Service configuration, and staff login unchanged. Staff authentication needs its own replacement project later.

Before the migration, API Gateway exposed the customer and worker OTP paths listed above. They are no longer public and their downstream controllers no longer exist. Google sign-in, refresh, worker selection, staff OTP, health probes, and the Razorpay webhook remain public as documented in `google-auth-api.md`. Worker API paths require `ROLE_WORKER`; ordinary `/api/v1/**` paths require `ROLE_CUSTOMER`; staff paths are role-specific. All other exchanges are denied.

## Current JWT issuance and validation

User Service and Worker Service independently issue Snabbit access JWTs with Spring Security's Nimbus encoder. They use HS256 and the shared `auth.jwt-secret`; startup requires at least 32 bytes. Tokens carry:

- `iss` from `auth.issuer`;
- `sub` equal to the customer, worker, or staff UUID;
- `iat` and `exp`;
- `roles`, containing `CUSTOMER`, `WORKER`, or a staff role.

The configured access-token lifetime is currently 15 minutes in User Service and Worker Service. There is no refresh token, session identifier claim, session table, token family, rotation, logout, logout-all, or revocation deny list.

API Gateway is the resource server and validates the HS256 signature, issuer, timestamps, and role claims. `AuthenticatedIdentityHeaderFilter` always removes caller-supplied identity headers, then derives and injects `X-Authenticated-User-Id`, `X-Authenticated-Worker-Id`, or staff headers from the validated JWT subject and roles. Downstream services trust these gateway-injected headers and their internal service-auth boundaries; they do not independently decode the public JWT.

Consequently, the new session-backed JWT can preserve downstream identity contracts by retaining UUID subjects and roles. Requiring a `sid` must be implemented at the gateway validation boundary so OTP-era tokens without it are rejected before identity headers are created.

## Notifications, WebSocket authentication, and device registration

Notification history and Android device registration are authenticated like other public APIs. Customer routes read `X-Authenticated-User-Id`; worker routes read `X-Authenticated-Worker-Id`. Device tokens are stored in Notification Service's `device_registration` table against recipient type and recipient UUID.

The WebSocket endpoints are `/api/v1/notifications/live` and `/api/v1/worker/notifications/live`. The gateway authenticates the upgrade request and injects the same trusted identity header. `NotificationWebSocketHandler` accepts the handshake only when that header contains a UUID; otherwise it closes the session. There is no separate WebSocket token format and no SSE implementation. Preserving the access JWT's subject and role therefore preserves notification-stream and device-registration identity behavior.

## Pre-migration login OTP implementation

`platform-common` defines `PhoneVerificationProvider` with start, check, and clear operations. `TwilioVerifyProvider` is the production adapter and `LoggingPhoneVerificationProvider` is the development adapter. The Twilio adapter uses Spring's shared `RestClient.Builder`, HTTP Basic authentication, and Twilio Verify verification/start and verification/check calls. Twilio generates and validates the digits. The provider configuration lives under `otp` and includes provider type, base URL, account credentials, API-key credentials, Verify service SID, TTL, and debug-code exposure.

Customer attempt and cooldown state is Redis-backed, not relational:

- request cooldown: `auth:otp:cooldown:<hashed-phone>`;
- attempt count: `auth:otp:attempts:<hashed-phone>`;
- legacy code-hash accessors existed in `RedisOtpChallengeStore`, although Twilio and the logging provider kept provider challenge state themselves; Step 6 removed those unused accessors;
- registration grants are random opaque values stored only by hash under `auth:registration:<grant-hash>` and consumed once.

Worker attempt and cooldown state was also Redis-backed under worker-specific keys. Step 6 removed the worker store and customer registration-grant implementation; their expiring Redis keys require no schema migration and age out naturally. There were no customer-login or worker-login OTP database tables or columns to drop. The shared User Service challenge store remains because staff OTP uses it. Gateway rate limiting additionally uses Redis and keys unauthenticated calls by remote address and authenticated calls by JWT subject.

Both customer and worker services normalize Indian mobile numbers with the shared `IndianPhoneNumber` helper. They enforce request cooldowns, provider verification, and a configured maximum attempt count. Customer verification returns an access token for an active existing profile or a registration grant for an unknown phone. Registration creates an active profile after the grant is consumed. Worker verification never creates an account: it requires exactly the active worker found by normalized phone. Suspended, deleted, paused, or unknown accounts cannot authenticate.

## Arrival OTP and other shared SMS concerns

Arrival OTP is a separate security domain owned by Booking Service and must survive removal of login OTP.

It has its own `ArrivalOtpProvider` port, `TwilioArrivalOtpProvider`, provider properties under `arrival-otp-provider`, stub implementation, command/dispatch services, errors, and tests. It persists one challenge per visit in `visit_arrival_challenge`, including destination phone, provider reference, lifecycle state, bounded attempts/sends, and expiry. Verification rate limiting uses separate Redis keys for the visit challenge and assigned worker. A successful GPS-validated arrival creates the challenge; successful OTP verification starts the visit.

Arrival OTP does not implement or inject `PhoneVerificationProvider` and does not use the customer or worker login Redis keys. It shares only infrastructure and potentially credential values: the common `RestClient.Builder`, Twilio Verify HTTP conventions, Twilio account/API credentials, and optionally the same Verify service SID through environment fallback. Its `arrival-otp-provider.*` configuration, Booking Service Flyway tables, Redis limits, and production-guard checks must remain.

The generic Notification Service SMS adapter is also login-independent and must remain. It is used for notification delivery and is not the login verification provider.

The staff OTP flow is the other direct consumer of the login `PhoneVerificationProvider`. This prevents deleting that shared port and adapter merely because customer and worker OTP endpoints are removed, unless staff authentication is migrated too.

## Customer profile and internal phone lookup

User Service owns `user_profile`. Today it contains UUID identity, required unique `phone_e164`, required display name, status, Plus state, rating aggregates, timestamps, and optimistic-lock version. It has no email, Google subject, picture, auth-provider, last-login, credentials-version, or seeded flag columns.

`GET /internal/v1/users/{userId}/contact` is protected by internal service authentication and returns the customer UUID, display name, and required phone. Booking Service uses this contract to resolve SELF home contact. Once phone becomes nullable, this response must also become nullable and SELF checkout must return `PHONE_REQUIRED` rather than inventing a number.

Existing phone-only accounts cannot be linked safely by phone during Google sign-in. The planned link keys are verified Google subject first and a one-time verified-email match only for a pre-provisioned unlinked profile.

## Worker profile and development worker identity

Worker Service owns `worker_profile`. Today it contains UUID identity, required unique `phone_e164`, required display name, persisted tier, status, location/radius, rating aggregates, join/create/update timestamps, and optimistic-lock version. It has no email, Google subject, auth-provider, last-login, or seeded flag column.

The opt-in local/dev seed recognizes its ten workers by the stable UUIDs in `DevSeedWorkerIds`. The Google-auth migration persists a seeded flag. `LocalWorkerSeedInitializer` upserts those identities, all service skills and compatibility, and rolling availability. Placeholder phone strings deliberately cannot receive SMS. All ten stable profiles receive the optional shared `dev-seed.worker-login-email` value; no worker login-phone override remains.

The Google-auth migration therefore needs an additive seeded marker, nullable email and Google subject, partial uniqueness for non-seeded identities, and replacement of the phone override with the configured shared worker-login email. The shared email must be applied only to the ten stable seed UUIDs; a non-seeded worker must never enter the picker. Google subject must not be stored on shared seeded profiles because one verified identity can select among all ten.

## Development customer identity

The opt-in local/dev customer seed identifies the demo customer by `dev-seed.demo-customer-email` / `DEV_SEED_DEMO_CUSTOMER_EMAIL`. If present it creates an active, unlinked profile with a stable UUID derived from the normalized email, a null phone and no Google subject, then grants Plus and applies the three stable default favourite worker IDs. First sign-in links that exact profile by verified email. User Service persists separate dev-seed state so removed defaults are not re-added repeatedly. There is no customer seeded flag.

The migration must replace phone-based demo identification with `DEV_SEED_DEMO_CUSTOMER_EMAIL`. The resulting profile is pre-provisioned and unlinked: active, email-backed, no Google subject, and eligible for the existing Plus/favourite provisioning. First sign-in with the same verified email links it once. No email literal belongs in code, migration, test, documentation example, or `.env.example`.

Customer and worker records live in separate service databases, so the same verified email may legitimately identify a customer profile and the shared seeded-worker picker. Client audience and endpoint/role separation remain mandatory.

## Redis usage

Redis currently supports:

- API Gateway request-rate limiting;
- customer/staff OTP cooldowns and attempt counters;
- customer registration grants;
- worker OTP cooldowns and attempt counters;
- Booking Service arrival-OTP verification limits;
- other existing service-specific transient coordination.

There is no current session storage or revoked-session deny list. The planned relational session tables remain authoritative in their owning services, while Redis adds immediate `sid` revocation checks at the gateway. Redis failure cannot lengthen access-token validity; the short JWT lifetime remains the fallback bound.

## Production configuration guard

`ProductionConfigurationGuard` runs only under the `production` profile. It rejects enabled development seed, offer auto-accept, or auto-progress; requires each service to declare production properties; rejects known local/development values; and enforces specific live providers. User Service continues to require `otp.*` Twilio Verify configuration for staff authentication. Worker Service no longer configures a phone-verification provider. Booking Service independently requires `arrival-otp-provider.*` with Twilio Verify.

The Google-auth work must add required Google client-ID configuration and reject any test verifier under production. It must also reject a configured shared worker-login email in production. When customer and worker OTP are removed, only login-specific OTP requirements should leave User and Worker Service. Booking Service arrival-OTP requirements and any staff/SMS requirements must remain.

## Migration constraints and implementation implications

- All schema changes must be additive Flyway migrations; applied migrations and existing user data are immutable.
- Customer phone must become nullable. Worker placeholder phones may remain, but normal worker Google linking uses pre-provisioned email and never creates a worker.
- There are no existing email or Google-subject indexes and no persisted seeded marker, so all are new schema responsibilities.
- Access JWT compatibility is deliberate only for downstream UUID subject and role routing. Adding mandatory `sid` intentionally invalidates every OTP-era access token.
- Session and refresh-token state belongs in User Service for customers and Worker Service for workers. Refresh tokens and selection tokens must be stored only as cryptographic hashes.
- Arrival OTP, generic SMS notification delivery, and staff OTP are separate retention concerns that must be covered by regression tests before any login-OTP cleanup.

## Progress

- **Step 0 complete:** the current authentication, JWT, notification identity, OTP, profile, seed, Redis, and production-guard behavior is audited in this document. Checkpoint: `d2336cc`.
- **Step 1 complete:** `platform-common` now owns a `GoogleIdentityVerifier` port and a Nimbus implementation that verifies RS256 signatures against Google's cached, rotation-aware published JWK set. It accepts only the two Google issuers, requires an unexpired token, checks the caller-specific configured audience, requires `email_verified`, requires `sub`, and optionally checks `nonce`. Only verified token claims produce subject, email, name, and picture attributes. Checkpoint: `cbf9e3c`.
- User Service reads `auth.google.customer-client-ids`; Worker Service reads `auth.google.worker-client-ids`. Both are comma-separated environment-backed lists. The production guard requires the applicable list, requires the real Google verifier, and rejects a stub value.
- The verifier stub exists only under test sources. Tests cover valid verification, bad signature, wrong issuer/audience, expiry, unverified email, missing subject, nonce mismatch, JWK caching, and signing-key rotation. Platform, User Service, and Worker Service tests pass.
- **Step 2 complete in the working checkpoint:** additive User and Worker Service migrations add verified Google identity metadata without rewriting prior migrations. Customer phone is nullable; an existing unlinked verified-email customer is linked once, otherwise an active customer is created without an onboarding gate. Disabled or conflicting accounts are rejected. Customers can save an owner-only normalized phone, and Booking Service returns `PHONE_REQUIRED` when SELF checkout has no phone.
- Worker sign-in resolves an existing Google subject first, then links exactly one active non-seeded pre-provisioned profile by verified email. It never creates a worker. The opt-in shared development identity returns only the ten stable active seeded workers and only worker ID, name, and current tier. Selection grants are random, stored only by token and subject hashes, expire after five minutes, are subject-bound, and are consumed once. Shared seeded profiles never store a Google subject. User, Worker, and Booking Service tests pass.
- **Step 3 complete in the working checkpoint:** User Service and Worker Service own additive session and rotated-refresh-token tables. Access tokens remain HS256 with the existing issuer and configurable 15-minute lifetime, and now include a required `sid` for customer and worker roles. Refresh tokens are opaque 256-bit random values stored only as SHA-256 hashes, rotate on every use, have a configurable sliding 180-day idle lifetime and optional absolute lifetime, and retain used hashes for family-reuse detection. A ten-second race grace returns `REFRESH_IN_PROGRESS`; later reuse revokes the family with `SESSION_REVOKED`.
- Sign-in, refresh, logout, logout-all, worker select-profile, and shared-session switch-profile are wired. Sessions record device metadata, enforce a configurable ten-session cap, support a per-account `sessions_valid_after` cutoff, and publish revoked session IDs to Redis for immediate gateway rejection. The gateway rejects customer/worker JWTs without `sid`, strips caller-supplied session headers, injects a trusted session header, preserves role separation, and falls back to the short signed-token lifetime if Redis is unavailable. Staff phone-OTP JWTs remain exempt from the new `sid` requirement so the unchanged staff login continues to work.
- Gateway IP rate limiting remains in front of public auth routes, and service-side account rate limiting uses only hashed Google subjects or account IDs. No ID, access, refresh, or selection token is logged. Platform, User, Worker, Gateway, Booking, and Notification tests pass; notification WebSocket and device registration retain the UUID subject/role contract.
- **Step 4 complete in the working checkpoint:** the development customer is now pre-provisioned solely by `DEV_SEED_DEMO_CUSTOMER_EMAIL`, with no phone and no Google subject, while retaining Plus and the three default favourites. All ten stable workers are explicitly seeded, carry `DEV_SEED_WORKER_LOGIN_EMAIL`, retain non-routable placeholder phones, full compatibility, tier data and rolling availability. The former customer and worker login-phone seed variables are removed from service configuration, Compose and `.env.example`.
- Seeded offer auto-accept and auto-progress continue to operate on stable seeded worker IDs. A real shared-login session changes only the authenticated actor and does not conflict with those development automations, so no automation behavior was changed. Production startup rejects either development login email even when the seed switch is false.
- **Step 5 complete in the working checkpoint:** `google-auth-setup.md` contains the Google Auth Platform consent/test-user and Web/Android OAuth-client checklist plus configuration variable names. `google-auth-api.md` is the implementation contract for sign-in, picker/select, refresh rotation, logout, shared profile switching, optional phone capture, errors, notification authentication and the current deep-link limitation. `.env.example`, Compose and service configuration expose names and safe defaults only; no identity value or secret is checked in.
- **Step 6 complete in the working checkpoint:** customer and worker phone-OTP login, customer OTP registration, their controllers and exclusive transient stores, Worker Service OTP configuration, and the corresponding public gateway routes are removed. Staff phone OTP, its shared provider/configuration and Redis challenge store remain unchanged. Booking arrival OTP and login-independent notification SMS remain unchanged. There were no login-OTP relational tables or columns to migrate.
- **Integration verification complete:** Docker-backed User Service and Worker Service tests run against PostgreSQL and Redis with a test-only Google verifier. They cover repeated customer sign-in, persisted refresh rotation, logout, logout-all, the exact ten-worker picker, single-use selection, shared-profile switching, and refresh after switching. Live execution exposed and fixed a `CHAR(64)`/JPA `VARCHAR` validation mismatch through additive V13 migrations that normalize refresh-token hash columns to `VARCHAR(64)`. The complete Gradle test suite passes with Docker-backed tests enabled.

## Gaps

- Staff authentication still uses phone OTP through the shared `PhoneVerificationProvider`. It is intentionally unchanged and needs its own replacement project later.
- Expired customer registration-grant and worker OTP Redis keys may remain briefly after deployment; they are unreachable and disappear through their existing TTLs.
- The mixed Android documentation files are intentionally not edited by this goal. After this work is complete, copy the API summary from `docs/backend/google-auth-api.md` into `docs/android/api-map.md` manually.
- `docs/backend/worker-tiers-favourites-api.md` still names the superseded phone-based seed variables. It is outside this goal's new-file-only documentation rule; use the email variables documented here and in `google-auth-setup.md`.
- Notification history and WebSocket snapshots do not yet expose a deep-link URI or structured target identifier. Android can route known event types only to top-level destinations until Notification Service adds that contract.
- Docker Desktop remains a local prerequisite for container-backed integration tests and Compose health checks; the tests skip explicitly on machines without a Docker engine.
