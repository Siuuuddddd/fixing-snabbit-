# Observability

Every service exposes these Actuator endpoints:

- `/actuator/health` for liveness and readiness probes.
- `/actuator/info` for build/application metadata.
- `/actuator/prometheus` for Prometheus-format JVM, HTTP, database-pool and Kafka client metrics.

Each deployable module includes `micrometer-registry-prometheus`; exposing the route in configuration alone is not sufficient. Prometheus should scrape the private service ports directly. The gateway must not publish service Actuator paths as customer routes.

Use `spring.application.name` as the stable service identity when configuring scrape targets and dashboards. Alert at minimum on sustained HTTP 5xx rates, request latency, database-pool exhaustion, Kafka consumer lag, outbox backlog, dead-letter volume, notification delivery failures and payment/refund failures. Money and booking identifiers must not be used as metric labels because their unbounded cardinality would overload the metrics backend.

Application logs must not contain OTP values outside explicitly enabled local development, service credentials, Razorpay secrets, complete phone numbers or exact worker coordinates. Location history remains in Worker Service's controlled database and customer disclosure is bounded by Booking Service's tracking policy.

