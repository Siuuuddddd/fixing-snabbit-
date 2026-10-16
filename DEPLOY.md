# Single-server deployment

This MVP can run all seven application containers, Kafka and Redis on one private Docker network while using a managed PostgreSQL cluster. Only the API Gateway should be reachable from the internet; terminate TLS in a reverse proxy and route it to port `8080`.

## 1. Prepare PostgreSQL

Create six isolated databases (and preferably six roles) in the managed cluster: `users`, `bookings`, `workers`, `wallets`, `support` and `notifications`. Allow inbound TLS connections only from the server. Each service runs its own Flyway migrations at startup and must receive only its own database credentials.

## 2. Build the service images

From the repository root, build one image per module:

```bash
for service in api-gateway user-service booking-service worker-service wallet-service support-service notification-service; do
  docker build --build-arg SERVICE="$service" -t "snabbit/$service:0.1.0" .
done
```

Run Kafka and Redis on the server or replace them with managed equivalents. Put every container on the same private Docker network and use its container DNS name in the internal URLs.

## 3. Supply configuration

Set `SPRING_PROFILES_ACTIVE=production` for a live deployment. The production guard stops startup when a required value is missing, still points to local development, or selects a non-production adapter.

Shared variables:

```text
KAFKA_BOOTSTRAP_SERVERS
REDIS_HOST, REDIS_PORT
JWT_SECRET                 # at least 32 random bytes
AUTH_ISSUER
AUTH_CODE_HMAC_SECRET       # independent high-entropy secret for email-code HMACs
AUTH_TERMS_VERSION
AUTH_CODE_TTL, AUTH_CODE_MAX_ATTEMPTS
AUTH_CODE_RESEND_COOLDOWN, AUTH_CODE_MAX_SENDS_PER_HOUR
AUTH_ARGON2_SALT_LENGTH, AUTH_ARGON2_HASH_LENGTH
AUTH_ARGON2_PARALLELISM, AUTH_ARGON2_MEMORY_KB, AUTH_ARGON2_ITERATIONS
AUTH_BCRYPT_STRENGTH        # fallback only when Bouncy Castle is unavailable
AUTH_EMAIL_RATE_LIMIT_WINDOW, AUTH_EMAIL_RATE_LIMIT_PER_IDENTITY
AUTH_EMAIL_RATE_LIMIT_PER_IP, AUTH_EMAIL_RATE_LIMIT_MAX_DELAY
AUTH_PHONE_RESEND_COOLDOWN, AUTH_PHONE_MAX_SENDS_PER_HOUR
AUTH_PHONE_MAX_ATTEMPTS, AUTH_PHONE_RATE_LIMIT_WINDOW, AUTH_PHONE_RATE_LIMIT_PER_IP
AUTH_REFRESH_IDLE_TTL, AUTH_REFRESH_ABSOLUTE_TTL, AUTH_SHORT_SESSION_ABSOLUTE_TTL
OTP_PEPPER
OTP_PROVIDER=twilio-verify
TWILIO_ACCOUNT_SID
TWILIO_AUTH_TOKEN
TWILIO_API_KEY_SID
TWILIO_API_KEY_SECRET
TWILIO_VERIFY_SERVICE_SID
ARRIVAL_OTP_PROVIDER=twilio-verify
ARRIVAL_TWILIO_VERIFY_SERVICE_SID # optional separate Verify service; may equal the login service SID
SERVICE_AUTH_TOKEN         # long random value shared only by the services
USER_SERVICE_URL, BOOKING_SERVICE_URL, WORKER_SERVICE_URL
WALLET_SERVICE_URL, SUPPORT_SERVICE_URL, NOTIFICATION_SERVICE_URL
WORKER_VETERAN_MONTHS, WORKER_EXPERT_MONTHS # tenure-tier thresholds; defaults 6 and 9
WORKER_STANDARD_SURCHARGE_MINOR, WORKER_VETERAN_SURCHARGE_MINOR, WORKER_EXPERT_SURCHARGE_MINOR
FAVOURITES_MAX_COUNT, FAVOURITES_REQUIRE_COMPLETED_VISIT # defaults 3 and true
WORKER_RATING_UPDATE_WINDOW, WORKER_RANK_PRIOR_MEAN, WORKER_RANK_PRIOR_WEIGHT
```

`DEV_SEED_ENABLED`, `DEV_SEED_AUTO_ACCEPT_OFFERS`, and `DEV_SEED_AUTO_PROGRESS` must remain `false` in production. `DEV_SEED_DEMO_CUSTOMER_EMAIL` and `DEV_SEED_DEMO_CUSTOMER_PHONE` must remain empty. The production startup guard rejects the development seed, seeded identities, or either seeded-worker automation even if every other provider setting is valid.

Booking Service uses a separate arrival-verification adapter and state machine even when it shares the same Twilio account or Verify service. Production startup requires `ARRIVAL_OTP_PROVIDER=twilio-verify`, API-key credentials, and a Verify service SID; the development `stub` adapter is rejected by the production guard. Never log OTP codes, provider secrets, full home-contact numbers, or Twilio response bodies.

Database variables use the service prefix, for example `USER_DB_URL`, `USER_DB_USERNAME`, `USER_DB_PASSWORD`; repeat with `BOOKING`, `WORKER`, `WALLET`, `SUPPORT` and `NOTIFICATION`. Use `jdbc:postgresql://...?...&sslmode=require` URLs supported by the managed provider.

External providers:

```text
GEOCODING_PROVIDER=google
GEOCODING_API_KEY

SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID
TWILIO_AUTH_TOKEN
TWILIO_FROM_NUMBER          # E.164 sender number

PUSH_PROVIDER=fcm
FCM_PROJECT_ID
FCM_SERVICE_ACCOUNT_FILE    # path to a read-only mounted Google service-account JSON file

RAZORPAY_MODE=live
RAZORPAY_KEY_ID
RAZORPAY_KEY_SECRET
RAZORPAY_WEBHOOK_SECRET

AUTH_EMAIL_SENDER=smtp
SMTP_HOST
SMTP_PORT
SMTP_USERNAME
SMTP_PASSWORD
SMTP_FROM
SMTP_AUTH
SMTP_TLS_ENABLED
```

Customer authentication email is sent directly by User Service over SMTP. Codes are never published to Kafka or an outbox. Use a dedicated random `AUTH_CODE_HMAC_SECRET`; do not reuse the JWT, database, SMTP, or service-auth secret. Customer and staff phone authentication both use the shared Twilio Verify adapter, while their endpoints, throttles, and account lookup remain separate. Production startup rejects a missing HMAC secret, local/Mailpit SMTP hosts, non-SMTP sender adapters, and any OTP provider other than `twilio-verify`. Never log or return an OTP. Arrival verification remains a separate booking workflow.

Register `https://<api-host>/webhooks/razorpay` in Razorpay and subscribe to captured-payment events. Do not expose database, Kafka, Redis or service ports publicly.

For Razorpay test-mode validation, run the same Wallet image with a non-production profile, `RAZORPAY_MODE=test`, test key ID/secret and a test webhook secret. Exercise one top-up, one split/online booking payment and one refund, and confirm the signed webhook changes the ledger only once. Switching to live requires `SPRING_PROFILES_ACTIVE=production` and live credentials; client-side payment success is never trusted.

## 4. Start and verify

Start databases first, then Kafka/Redis, then the services, and finally the gateway. Verify each private service at `/actuator/health` and the gateway at `https://<api-host>/actuator/health`. Keep provider credentials and the FCM JSON outside the image and rotate them through the server's secret store.
