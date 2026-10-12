# Multi-day booking workflow audit

## Status

Status recovered from the uncommitted implementation and tests on 2026-10-12. `PARTIAL` means that production code exists but at least one required contract, failure path, migration, or explicit test is still missing.

1. **PARTIAL — Config-driven limits.** Booking configuration defaults to a 10-day Asia/Kolkata window, 2 minimum visits, and a maximum equal to the window, but downstream visit-index constraints and stale seven-day references still need auditing and migration.
2. **DONE — Request shape and backward compatibility.** The preferred `visits: [{date, startTime}]` shape converts in the configured zone, duration remains booking-level, and legacy `startsAt` remains accepted.
3. **PARTIAL — Validation.** Window, lead time, duration, operating hours, and duplicate-date validation exist with indexed visit errors, but visit-count failures are still generic and the complete requested boundary/error test matrix is missing.
4. **PARTIAL — Atomic holds and alternatives.** Candidate preflight reports every unavailable visit with up to three alternatives and local persistence is transactional, but race-time hold failures remain generic and the required competing-customer/all-failures tests are incomplete.
5. **DONE — Expert preference.** `ANY`, `FAVOURITE` with ownership validation and worker ID, and `VETERAN` are accepted and passed through matching.
6. **DONE — Fallback policy contract.** `AUTO_ASSIGN` and `SKIP_AND_REFUND` are accepted, `ASK_FIRST` is rejected clearly, and the gap is documented.
7. **PARTIAL — Per-visit assignment.** Visits select workers independently and matching has favourite, compatibility, tier, availability, and distance logic, but required assignment-type names and complete fallback/refund semantics and tests are unfinished.
8. **PARTIAL — Pricing.** Per-visit quotes and the configurable free Plus Veteran upgrade exist, but fallback price-cap behavior is not fully proven by focused tests.
9. **PARTIAL — Responses.** Booking totals, effective hold expiry, duration, price, worker, assignment, and refund fields are additive, but the required assignment enum values and explicit local `date`/`startTime` response fields are not complete.
10. **PARTIAL — Single payment and visit allocation.** One wallet/UPI payment and signed-webhook confirmation remain intact, and visit refund amounts are durable, but explicit per-visit payment allocation coverage is incomplete.
11. **PARTIAL — Payment hold extension and release.** Bounded extension and payment-start failure cancellation exist; expiry/pass-release atomicity and extension behavior still need the requested tests.
12. **PARTIAL — Cancellation and refunds.** Whole-booking and per-visit cancellation paths use durable UUID refund keys and Wallet supports multiple refunds, but the full cancellation/refund test matrix is incomplete.
13. **PARTIAL — Worker dropout.** Same-tier replacement or one-visit refund code and outbox writes exist, but end-to-end behavior and required integration tests are missing.
14. **DONE — Visit lifecycle events.** `VisitAssigned`, `VisitAssignmentChanged`, and `VisitRefunded` are mapped to durable booking/notification paths; the separate Notification/Support startup issue may still limit Docker verification.
15. **DONE — Household snapshot.** Household data is fetched once during matching and copied to the booking and each persisted visit.

## Scope and invariants

This document audits the existing backend against the multi-day workflow in the supplied UML diagrams. The implementation target is a configurable advance window that defaults to 10 calendar days in `Asia/Kolkata`, including today. A multi-day booking remains one booking and one payment, but every visit is independently scheduled, assigned, priced, cancellable, and refundable. Holding a plan is atomic: either every requested visit is held or none is retained.

The duration remains booking-level. The current persistence model, matching API, quote calculation, and Android contract all use one duration for every visit. Keeping that invariant avoids an unnecessary breaking migration while still allowing each visit to have its own date and start time.

## Existing behaviour

- `POST /api/v1/bookings/match-and-hold` accepts a booking-level duration and a list of distinct `startsAt` instants. The internal create endpoint also accepts one instant and worker per visit.
- `BookingWindowPolicy` converts instants using the configured zone and checks duration and operating hours, but hardcodes `today.plusDays(7)` and has no configurable lead time.
- Multi-day validation is duplicated in matching and lifecycle services and hardcodes 2 through 7 visits. The database also constrains `slot_hold.visit_index` to 0 through 6.
- Matching calls Worker Service once per visit. Worker Service filters skills, availability, household compatibility and radius, then ranks favourites, rating, and distance. It returns tier, distance, favourite status, and price.
- Plus multi-day matching currently tries to use one common worker across all visits. This is not required by the new workflow and can reduce availability.
- Booking creation locks worker/day capacity rows in stable order and persists the booking, all holds, offers, and outbox events in one database transaction. This gives local all-or-none persistence. Plus-pass reservation is a remote step and is released best-effort if local creation fails.
- A held visit stores worker, start/end, and expiry. Price exists only as booking totals; assignment type, preference, fallback policy, visit refund status, visit cancellation state, and household snapshot do not exist per visit.
- Slot failure is currently a generic `409` message. It does not identify all failed visits or provide nearby times.
- Workers accept offers before payment. Any rejected/expired offer currently fails the entire booking rather than reassigning or refunding one visit.
- Payment is one wallet/online split payment. Signed Razorpay webhooks durably record capture and publish `WalletPaymentSucceeded`; Booking Service confirms all holds after that event. Payment initiation does not extend holds.
- Refund persistence is one unique refund per booking payment, so repeated partial visit refunds are not representable. Whole-booking cancellation and refund are implemented; per-visit cancellation is not.
- Booking and Wallet services already use transactional outboxes and idempotent consumers. Notification Service persists notifications/delivery attempts, but it has no visit-assigned, assignment-changed, or visit-refunded templates.
- Household requirements are snapshotted once on the booking and reused for every match. There is no redundant household API call per visit, but the snapshot is not copied onto each visit row.

## Missing behaviour

- Configurable advance days, minimum/maximum multi-day visits, minimum lead time, payment hold-extension duration, maximum total hold lifetime, and free Plus Veteran upgrade flag.
- A preferred request shape containing per-visit local `date` and `startTime`, with strict Asia/Kolkata conversion and structured validation errors for every invalid visit.
- Explicit `ANY`, `FAVOURITE` (with worker ID), and `VETERAN` expert preferences; explicit `AUTO_ASSIGN` and `SKIP_AND_REFUND` fallback policies. `ASK_FIRST` is intentionally unsupported and must be rejected.
- Atomic preflight that reports every unavailable visit and up to three nearby alternatives before creating any booking/hold/pass state.
- Per-visit quoted price, original/preferred worker, assignment type, refund state/amount/idempotency key, cancellation state, and household snapshot.
- Per-visit assignment/reassignment that prefers the requested favourite when free, otherwise the nearest compatible worker in the required tier. A common worker across dates must not be required.
- Per-visit and whole-booking cancellation/refund APIs and durable UUID refund idempotency keys.
- Worker-dropout reassignment, or automatic visit refund when policy/availability requires it, with durable outbox events.
- Response fields for visit duration, quoted price, worker/assignment type, refund state, total, and effective hold expiry.
- Integration coverage for concurrent overlapping plans, atomic rollback, partial refunds, signed payment confirmation, and outbox delivery.

## Implementation plan

1. Add booking configuration with safe defaults: 10 advance calendar days including today, minimum 2 visits, maximum equal to the advance window, configurable lead time, payment extension, maximum total hold lifetime, and Plus Veteran upgrade enabled.
2. Add a preferred `visits: [{date, startTime}]` request while retaining legacy `startsAt`. Exactly one representation is accepted. Convert local values in the configured booking zone and retain the legacy response fields.
3. Centralize plan validation and return RFC 9457 problem details with indexed visit errors. Add a structured atomic-hold conflict carrying every failed visit and at most three valid nearby start times.
4. Add a Flyway migration; do not edit prior migrations. Expand the visit-index constraint and add per-visit assignment, pricing, snapshot, cancellation, and refund columns. Add payment-attempt timing to the booking where needed.
5. Remove common-worker selection. Extend matching filters for favourite and Veteran preferences and preserve the rule that the final per-visit price cannot exceed the original quote. When Plus Veteran upgrade is enabled, Veteran selection does not add its surcharge.
6. Preflight all visits and alternatives before reserving a Plus pass or entering the lifecycle transaction. Persist all holds in one transaction with stable locking. If a later local operation fails, roll back every local hold; release the pass through the existing compensating call.
7. Extend hold expiry only when payment is started, bounded by the booking's configured maximum total hold lifetime. Payment failure/expiry releases every hold and the pass through durable events/compensation.
8. Record per-visit allocation in Wallet Service, allow multiple partial refunds per payment, and use the Booking Service-generated refund UUID as the durable idempotency key. Preserve the existing whole-booking refund endpoints and events.
9. Publish `VisitAssigned`, `VisitAssignmentChanged`, and `VisitRefunded` via the booking outbox and add durable customer/worker notification templates.
10. Add focused unit and Testcontainers integration tests, update API/progress/gap documentation, then run root tests and rebuild affected Docker services sequentially.

## Backward compatibility

- Existing callers may continue sending `startsAt`; its instants remain authoritative and are validated in Asia/Kolkata.
- Existing single-visit and internal create endpoints remain available.
- Existing booking totals and whole-booking cancellation fields remain in responses. New per-visit fields are additive.
- Existing booking payment behaviour remains one payment. Per-visit allocation is additive accounting metadata.
- Existing enum values and old Flyway migrations are not rewritten.

## Explicit gap

`ASK_FIRST` fallback is not implemented because it needs a durable customer-decision state, expiry policy, and resume API that do not exist in the current lifecycle. Requests using it will receive a validation error directing clients to `AUTO_ASSIGN` or `SKIP_AND_REFUND`.
