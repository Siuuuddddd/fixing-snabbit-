# Customer authentication

Registered customers authenticate with an Indian mobile number and a one-time password.

## Flow

1. `POST /api/v1/auth/otp/request` with `{"phoneNumber":"+919876543210"}`.
2. User Service accepts only `+91` E.164 mobile numbers beginning with 6–9. It applies a one-minute Redis cooldown, creates a cryptographically random six-digit code and stores only an HMAC of that code for five minutes.
3. `POST /api/v1/auth/otp/verify` with the same phone and OTP.
4. A successful one-time verification returns a 15-minute HS256 access token with the registered customer UUID as `sub` and `CUSTOMER` in `roles`.
5. Send the token as `Authorization: Bearer <token>` to customer APIs.

OTP challenges allow at most five failed attempts and are deleted after success or exhaustion. Unknown numbers receive the same generic request response but no OTP is stored or delivered, reducing account enumeration.

## Gateway boundary

The API Gateway permits authentication, health and the Razorpay webhook without a bearer token. Other `/api/v1/**` routes require a valid token with the configured issuer and signature. The gateway always removes any incoming `X-Authenticated-User-Id` value and writes the verified JWT subject instead.

User, Booking and Wallet customer routes compare that trusted subject with the requested customer or owned resource. Booking-to-User lookups use `/internal/v1/**` and are not routed publicly.

## Service-to-service authentication

User, Booking, Worker and Wallet servlet services apply a request filter to every `/internal/v1/**` route. Support and Notification apply the equivalent reactive filter. The filter requires `X-Service-Token` and compares it to `SERVICE_AUTH_TOKEN` in constant time. Each service uses an explicit internal-client factory that adds the header to service calls. External clients such as Razorpay deliberately use the ordinary builder, preventing the internal credential from leaving the service network.

The configured token must contain at least 32 bytes. Checked-in local defaults are for Docker/local development only; every deployed environment must inject and rotate a unique secret. The API Gateway does not route internal paths, Android never receives this credential, and service ports must remain on a private network. For production hardening, replace the shared prototype credential with mTLS or short-lived workload identities so each calling service has an independently auditable identity.

## Prototype delivery and secrets

`LoggingOtpDelivery` is a replaceable development adapter. It writes the OTP to the User Service log with the phone number masked. API responses expose the code only when `OTP_EXPOSE_CODE=true`; the secure default is `false`.

User Service and API Gateway must use the same `JWT_SECRET`, which must be at least 32 bytes. Set a unique `OTP_PEPPER` and `SERVICE_AUTH_TOKEN` as well. The checked-in defaults are local-development values and must never be used outside a local prototype.

The seeded registered customer is `+919876543210`. Workers use the equivalent role-separated flow documented in [worker-api.md](worker-api.md). Production SMS delivery remains an upcoming capability.
