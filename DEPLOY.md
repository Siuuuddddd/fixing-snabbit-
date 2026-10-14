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

`DEV_SEED_ENABLED`, `DEV_SEED_AUTO_ACCEPT_OFFERS`, and `DEV_SEED_AUTO_PROGRESS` must remain `false` in production. The production startup guard rejects the development seed or either seeded-worker automation even if every other provider setting is valid.

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
```

Register `https://<api-host>/webhooks/razorpay` in Razorpay and subscribe to captured-payment events. Do not expose database, Kafka, Redis or service ports publicly.

For Razorpay test-mode validation, run the same Wallet image with a non-production profile, `RAZORPAY_MODE=test`, test key ID/secret and a test webhook secret. Exercise one top-up, one split/online booking payment and one refund, and confirm the signed webhook changes the ledger only once. Switching to live requires `SPRING_PROFILES_ACTIVE=production` and live credentials; client-side payment success is never trusted.

## 4. Start and verify

Start databases first, then Kafka/Redis, then the services, and finally the gateway. Verify each private service at `/actuator/health` and the gateway at `https://<api-host>/actuator/health`. Keep provider credentials and the FCM JSON outside the image and rotate them through the server's secret store.
