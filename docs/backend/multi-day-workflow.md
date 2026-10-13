# Multi-day booking workflow audit

## Status

Final status after implementation and verification on 2026-10-12.

1. **DONE — Config-driven limits.** Booking configuration defaults to a 10-day Asia/Kolkata window, 2 minimum visits, and a maximum equal to the configured window; additive migrations remove downstream seven-visit index ceilings.
2. **DONE — Request shape and backward compatibility.** The preferred `visits: [{date, startTime, durationHours}]` shape converts in the configured zone and supports a different duration for every visit. The top-level `durationHours` and legacy `startsAt` remain accepted as fallbacks for older clients.
3. **DONE — Validation.** Visit count, distinct local dates, 10-day bounds, same-day lead time, whole-hour duration, and operating hours are enforced; invalid visits return indexed structured errors.
4. **DONE — Atomic holds and alternatives.** Candidate and lock-time failures report every unavailable visit with up to three nearby alternatives; the booking, offers, and holds roll back together and any pass reservation is compensated.
5. **DONE — Expert preference.** `ANY`, `FAVOURITE` with ownership validation and worker ID, and `VETERAN` are accepted and passed through matching.
6. **DONE — Fallback policy contract.** `AUTO_ASSIGN` and `SKIP_AND_REFUND` are accepted, `ASK_FIRST` is rejected clearly, and the gap is documented.
7. **DONE — Per-visit assignment.** Visits select workers independently; favourite, same-tier nearest, Plus Veteran upgrade, skip/refund, and dropout decisions use the required assignment types and preserve compatibility constraints.
8. **DONE — Pricing.** Per-visit quotes are persisted, reassignment never changes them, and the configurable free Plus Veteran upgrade is capped at the original base quote.
9. **DONE — Responses.** Responses expose local date/start time, duration, quoted price, nullable assigned worker, exact assignment type, refund state, total, and effective hold expiry per plan/visit.
10. **DONE — Single payment and visit allocation.** One wallet/UPI payment remains authoritative, per-visit amounts drive independent durable refunds, and only the signed provider webhook confirms online payment.
11. **DONE — Payment hold extension and release.** Payment start extends every hold only to the configured total-lifetime cap; failure/expiry releases all local holds and emits the durable booking event that releases a reserved pass.
12. **DONE — Cancellation and refunds.** Whole-booking, one-visit, and platform cancellation paths use durable UUID visit-refund keys; Wallet supports multiple idempotent partial refunds per payment.
13. **DONE — Worker dropout.** `AUTO_ASSIGN` tries the nearest compatible same-tier replacement and emits an assignment-change event; `SKIP_AND_REFUND` or no match refunds only that visit.
14. **DONE — Visit lifecycle events.** `VisitAssigned`, `VisitAssignmentChanged`, and `VisitRefunded` are mapped to durable booking/notification paths; the separate Notification/Support startup concern did not reproduce during final Docker verification.
15. **DONE — Household snapshot.** Household data is fetched once during matching and copied to the booking and each persisted visit.

## Scope and invariants

This document audits the existing backend against the multi-day workflow in the supplied UML diagrams. The implementation target is a configurable advance window that defaults to 10 calendar days in `Asia/Kolkata`, including today. A multi-day booking remains one booking and one payment, but every visit is independently scheduled, assigned, priced, cancellable, and refundable. Holding a plan is atomic: either every requested visit is held or none is retained.

Duration is a per-visit value. Every selected date carries its own whole-hour duration through availability, matching, pricing, persistence, responses, and Android navigation. Older clients can omit visit durations and continue using the top-level `durationHours` value for every visit.

## Final behaviour

- The preferred request is `visits: [{date, startTime, durationHours}]`; legacy `startsAt` plus top-level `durationHours` remains supported, and exactly one visit-time representation may be supplied.
- The default window is local day 0 through day 9 in `Asia/Kolkata`. Multi-day dates are distinct but need not be consecutive; every visit has its own 1–4 whole-hour duration.
- Availability and match-and-hold validate every visit. A `409` atomic-hold problem contains `visitFailures`, and each failure contains its index, requested instant, reason, and at most three valid nearby alternatives.
- Each visit is matched independently. Responses use `PREFERRED`, `FALLBACK_SAME_TIER`, `FALLBACK_PLUS_UPGRADE`, or `UNASSIGNED`; an unassigned/refunded visit returns a null worker while retaining its audit history internally.
- Booking, holds, offers, and outbox records are one local transaction. Cross-service pass release uses the existing durable saga/outbox compensation rather than pretending to provide a distributed database transaction.
- One signed-webhook-confirmed booking payment covers the full plan. Persisted visit quotes allocate that total and become the authoritative ceiling for later refund/reassignment decisions.
- Payment start extends all holds to the smaller of the configured extension or maximum total lifetime. Failure and expiry release every hold and trigger durable pass compensation.
- A visit can be cancelled/refunded independently. Wallet allocates wallet/online portions across multiple refund rows and uses the booking-generated refund UUID as the idempotency key.
- Worker dropout recovery never requires one worker across the plan: it tries a nearest same-tier replacement for that visit, otherwise refunds only that visit.
- Household requirements are fetched once and snapshotted onto both the booking and every visit; production matching never trusts client-supplied price, duration, refund, or assignment outcomes.

## Implemented design

1. Add booking configuration with safe defaults: 10 advance calendar days including today, minimum 2 visits, maximum equal to the advance window, configurable lead time, payment extension, maximum total hold lifetime, and Plus Veteran upgrade enabled.
2. Add a preferred `visits: [{date, startTime, durationHours}]` request while retaining legacy `startsAt` and top-level `durationHours`. Exactly one visit-time representation is accepted. Convert local values in the configured booking zone and retain the legacy response fields.
3. Centralize plan validation and return RFC 9457 problem details with indexed visit errors. Add a structured atomic-hold conflict carrying every failed visit and at most three valid nearby start times.
4. Add a Flyway migration; do not edit prior migrations. Expand the visit-index constraint and add per-visit assignment, pricing, snapshot, cancellation, and refund columns. Add payment-attempt timing to the booking where needed.
5. Remove common-worker selection. Extend matching filters for favourite and Veteran preferences and preserve the rule that the final per-visit price cannot exceed the original quote. When Plus Veteran upgrade is enabled, Veteran selection does not add its surcharge.
6. Preflight all visits and alternatives before reserving a Plus pass or entering the lifecycle transaction. Persist all holds in one transaction with stable locking. If a later local operation fails, roll back every local hold; release the pass through the existing compensating call.
7. Extend hold expiry only when payment is started, bounded by the booking's configured maximum total hold lifetime. Payment failure/expiry releases every hold and the pass through durable events/compensation.
8. Record per-visit allocation in Wallet Service, allow multiple partial refunds per payment, and use the Booking Service-generated refund UUID as the durable idempotency key. Preserve the existing whole-booking refund endpoints and events.
9. Publish `VisitAssigned`, `VisitAssignmentChanged`, and `VisitRefunded` via the booking outbox and add durable customer/worker notification templates.
10. Add focused unit and Testcontainers integration tests, update API/progress/gap documentation, then run root tests and rebuild affected Docker services sequentially.

## Verification

- `./gradlew.bat test --rerun-tasks --no-daemon --max-workers=1`, followed by the final focused booking integration regression: 238 tests, 0 failures, 0 errors, 0 skipped across 75 reports; all 40 Gradle test tasks executed in the forced run.
- Docker-backed coverage includes atomic rollback across different visit times, two customers competing for the last slot, payment hold extension/failure release, expired-hold release, a real-database dropout reassignment, and independent idempotent visit refunds.
- User and Worker Testcontainers suites can log a harmless shutdown-time outbox poll after PostgreSQL has stopped; the tests still complete successfully. The five changed service images were rebuilt and recreated sequentially, Flyway reached user v8, worker v8, booking v15, and wallet v8, and the gateway plus ports 8081–8086 all reported `UP`; the known Notification/Support `RestClient.Builder` startup concern did not reproduce.

## Backward compatibility

- Existing callers may continue sending `startsAt` with top-level `durationHours`; those instants and the shared fallback duration remain authoritative and are validated in Asia/Kolkata.
- Existing callers using `visits` may omit each visit's `durationHours` when a top-level fallback is present.
- Existing single-visit and internal create endpoints remain available.
- Existing booking totals and whole-booking cancellation fields remain in responses. New per-visit fields are additive.
- Existing booking payment behaviour remains one payment. Per-visit allocation is additive accounting metadata.
- Existing enum values and old Flyway migrations are not rewritten.

## Explicit gap

`ASK_FIRST` fallback is not implemented because it needs a durable customer-decision state, expiry policy, and resume API that do not exist in the current lifecycle. Requests using it will receive a validation error directing clients to `AUTO_ASSIGN` or `SKIP_AND_REFUND`.
