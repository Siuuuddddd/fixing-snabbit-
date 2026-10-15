# Customer email authentication

## Scope

This work adds customer email/password authentication alongside the committed Google sign-in and rotating JWT session system. It is backend-only. Worker Google authentication, the development worker picker, staff phone-OTP authentication, and booking arrival OTP are outside this change and must remain unchanged.

No real identity, password, code, token, or secret belongs in source, tests, documentation, example configuration, events, or logs.

## Step 0 audit

### Customer profile and identity schema

User Service owns `user_profile`. The current mapped columns are:

- `id`, `phone_e164` (nullable, unique), `display_name`, and `status` (`ACTIVE`, `SUSPENDED`, or `DELETED`);
- Plus entitlement cache (`plus_active`, `plus_expires_at`) and rating aggregates;
- Google identity fields `google_sub` (nullable, unique), `email` (nullable), `email_verified`, `picture_url`, `auth_provider`, and `last_login_at`;
- `sessions_valid_after`, timestamps, and optimistic-lock `version`.

Migration V11 added a case-insensitive unique index on `LOWER(email)` for non-null email values and a partial unique index on non-null `google_sub`. Email/password authentication can therefore reuse `email` and `email_verified`, but a new migration must expand the single `auth_provider` enum-like column into provider-set semantics and add password, verification timestamp, and terms fields. Email normalization will trim and lowercase only; it will not rewrite provider aliases.

The present `UserAuthProvider` value is a single `PHONE` or `GOOGLE` value. It cannot represent a Google account that later adds a password, so it must be migrated without breaking existing rows or Google sign-in.

### Google account resolution and development seed

`CustomerGoogleAccountService` resolves by verified Google subject first, then by case-insensitive email under a write lock, and otherwise creates an active customer. It updates the verified email, display name, picture, provider, and last-login time. Email/password linking must extend this path so that Google verification of an unverified password account marks the email verified and deletes the password hash before issuing a session.

The local/dev seed is opt-in and pre-provisions a customer from `dev-seed.demo-customer-email`, supplied by `DEV_SEED_DEMO_CUSTOMER_EMAIL`. It trims and lowercases the configured value, derives a stable UUID, leaves phone and Google subject null, and preserves Plus and default favourites. Verified password sign-up must attach to this existing profile rather than create a second profile, so seeded perks remain intact.

### Sessions, refresh rotation, logout, and revocation

User Service owns `customer_session` and `customer_rotated_refresh_token`. Access JWTs are HS256 tokens with issuer, customer UUID subject, `CUSTOMER` role, and mandatory `sid`. Refresh tokens are opaque random values stored only as SHA-256 hashes. A refresh rotates the token, records the previous hash, enforces idle and optional absolute expiry, and detects reuse after a short race grace period.

`CustomerSessionService.create` enforces the active-session cap and returns the normal access-token, expiry, refresh-token, and customer payload. `logout` revokes the current session and publishes the session ID to Redis for immediate gateway rejection. `logoutAll` delegates to `invalidateAllSessions`; that advances `user_profile.sessions_valid_after` and revokes every active session. Refresh rejects revoked, expired, missing-account, or cutoff-invalidated sessions. The gateway also rejects customer/worker JWTs without `sid`; staff JWTs remain exempt.

Password reset must call `invalidateAllSessions` and issue no token. Authenticated password change needs a new session operation that revokes every other customer session, preserves the caller's current session, rotates its refresh credential, and returns a fresh token pair.

### Existing rate limiting

API Gateway applies its Redis `RequestRateLimiter` to all routes, keyed by authenticated JWT subject when present and otherwise by remote IP. The current service-side `RedisCustomerAuthRateLimiter` hashes an account key, increments `auth:customer:rate:<scope>:<hash>`, and applies one configured fixed window and count. Google sign-in and refresh use it.

Email authentication requires extending or replacing this customer limiter to support endpoint-specific per-email-plus-IP and per-IP limits, progressive delay, and `retryAfterSeconds`, while keeping Redis keys free of raw emails. Limits must not permanently lock an account or reveal which limit fired.

### Password encoding and staff authentication

There is no existing `PasswordEncoder` bean and User Service currently has no password dependency or password column. Staff authentication does not use passwords: `StaffAuthenticationService` normalizes an Indian phone number, uses the shared `PhoneVerificationProvider`, tracks cooldown and attempts in the existing Redis OTP store, resolves an active `staff_account`, and issues a staff JWT without a customer session ID.

Staff phone OTP, its `otp.*` configuration, `PhoneVerificationProvider`, Twilio adapter, challenge store, routes, and staff account schema must remain unchanged. Customer password support will use its own encoder configuration and must not alter staff behavior.

### Email delivery capability

No Spring Mail dependency, `JavaMailSender`, SMTP adapter, email port, or email template exists in the repository. User Service will therefore own a new `AuthEmailSender` port and a Spring Mail SMTP adapter. Verification and reset codes will be delivered directly by User Service and must never enter Kafka, outbox events, analytics, exception messages, or logs. Local development will use Mailpit; production must require real SMTP configuration.

### Gateway routes

API Gateway currently permits customer `POST /api/v1/auth/google` and `POST /api/v1/auth/refresh`, worker Google/session entry routes, staff auth, health probes, and the Razorpay webhook. Customer logout and logout-all require `ROLE_CUSTOMER`; other `/api/v1/**` routes require the applicable role.

Register, verify-email, resend-verification, email login, forgot-password, and reset-password must be added to the public allow-list. Change-password remains protected by `ROLE_CUSTOMER`. Existing Google, refresh, logout, and logout-all routes remain unchanged.

### Production configuration guard

The shared production guard rejects enabled development seed/automation, development login emails, missing required-property declarations, missing values, known local-development values, and unsupported live-provider selections. User Service currently requires production Google identity configuration and Twilio Verify settings because staff still uses phone OTP.

Email authentication must add the HMAC secret and real SMTP properties to User Service's production required-property list. The guard must reject Mailpit/local SMTP endpoints and any fake email sender in production, while retaining every staff OTP requirement.

## Decisions

- Customer email is the case-insensitive identity key and is normalized only by trim plus lowercase.
- Password credentials coexist with Google through provider-set semantics. Google linking to an unverified password account deletes the password hash to prevent pre-hijacking.
- New accounts remain unusable and receive no session until email verification succeeds.
- Codes are generated securely and persisted only as HMACs in User Service. Verification and reset have separate purposes.
- SMTP delivery is synchronous through a dedicated User Service port; codes never cross Kafka or an outbox.
- The existing customer session response and refresh behavior are reused. Password reset revokes all sessions; password change preserves only the caller session and rotates its credentials.
- Phone remains an optional profile field and is not part of email authentication.

## Progress

- Step 0 complete: committed Google/JWT foundation verified; customer schema, session lifecycle, rate limiting, staff authentication, email capability, gateway routes, production guard, and development seed audited.

## Gaps

- Password hashing, the common-password resource, email challenges, SMTP delivery, endpoint throttling, and password endpoints do not yet exist.
- The single `auth_provider` field cannot represent multiple providers and needs an additive migration plus compatible domain mapping.
- The current customer session response does not expose `emailVerified`, `hasPassword`, or `authProviders`.
- The current rate-limit error does not include `retryAfterSeconds`, and the service limiter does not combine account and IP dimensions.
- Mailpit and SMTP production validation are not configured.
