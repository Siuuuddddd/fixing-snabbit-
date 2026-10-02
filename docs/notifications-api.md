# Notifications and live booking updates

Notification Service consumes `booking-events` and `wallet-events` in the `notification-service` consumer group. It resolves the authoritative booking or wallet owner through private HTTP APIs before opening its local transaction, then claims the event ID and stores the customer notification plus delivery jobs atomically.

## Customer APIs

All routes require a customer JWT through the API Gateway and use its trusted `X-Authenticated-User-Id` header.

- `GET /api/v1/notifications?afterNotificationId=0` returns up to 100 notifications in durable ascending sequence order.
- `POST /api/v1/notifications/{notificationId}/read` marks an owned notification read.
- `POST /api/v1/notifications/devices` registers or reactivates an Android push token.
- `DELETE /api/v1/notifications/devices/{deviceId}` deactivates an owned device.
- WebSocket `/api/v1/notifications/live?afterNotificationId=0` sends JSON notification snapshots after the supplied durable cursor.

Android should persist the greatest `notificationId` it has processed. After disconnect or app restart, reconnect with that value; the WebSocket polls PostgreSQL rather than instance-local memory, so replay works across Notification Service instances. REST history provides the same recovery path when a WebSocket is unavailable.

## Worker APIs

Worker JWTs use the equivalent role-separated routes:

- `GET /api/v1/worker/notifications?afterNotificationId=0`
- `POST /api/v1/worker/notifications/{notificationId}/read`
- `POST /api/v1/worker/notifications/devices`
- `DELETE /api/v1/worker/notifications/devices/{deviceId}`
- WebSocket `/api/v1/worker/notifications/live?afterNotificationId=0`

`WorkerOffersCreated` is fanned out idempotently to one notification per distinct worker. Multi-day offers for the same worker remain in one notification, and each stored payload contains only that worker's offers. The worker feed remains the authoritative source for current offer state; notifications wake the app but cannot extend an expired offer.

## Push and SMS delivery

Every stored notification creates a push delivery. Critical events—payment deadlines/failures, confirmations, arrival, cancellations and refunds—also create an SMS delivery. A short SQL lease using `FOR UPDATE SKIP LOCKED` lets multiple service instances claim jobs without duplicate intentional sends. Failures use exponential delay and become `DEAD` after five attempts.

The checked-in adapters log masked development deliveries. They intentionally never log full device tokens or phone numbers and are registered only under the `local` and `test` Spring profiles. The checked-in default profile is `local`; a non-local deployment fails to start until it provides real `PushDeliveryAdapter` and `SmsDeliveryAdapter` beans. Production must supply FCM and an Indian SMS provider while preserving the existing leased job and acknowledgement boundary.

Verified Plus purchases are also notification facts. Customers receive a critical `Plus payment verified` notification from the durable `PlusSubscriptionPurchased` wallet event; client-side checkout callbacks never create this message.

Push tokens are stored only in Notification Service. Phone numbers remain owned by User or Worker Service and are fetched through the matching protected contact endpoint outside the delivery transaction. Notification Service never reads User, Worker, Booking or Wallet databases directly.
