# Worker tiers, favourites, ratings, and dev seed

## Step 0 audit

This document records the backend behaviour that existed before the worker-tier/favourites work began. It is intentionally an audit, not a statement that the target feature is already complete. The implementation must extend the existing contracts below rather than creating parallel concepts.

### Ownership and current worker model

- Worker Service owns `worker_profile`, `worker_skill`, date-specific `worker_availability`, `worker_compatibility`, and `worker_rating`.
- `worker_profile` stores the worker UUID, phone, display name, persisted `tier`, status, current latitude/longitude, personal service radius, `rating_sum`, `rating_count`, timestamps, and optimistic-lock version. There is no explicit `joined_at`; the only tenure-like value is immutable `created_at`. The new implementation therefore needs an additive join-date column rather than treating mutable profile timestamps as tenure.
- Skills are enabled rows keyed by `(worker_id, service_code)`. The current catalogue contains `HOUSE_CLEANING`, `DISH_WASHING`, `LAUNDRY`, and `TOILET_CLEANING`, with prices represented as integer minor units (paise).
- Availability is one row per worker and local service date, with an `available_from`, `available_until`, status, and optimistic-lock version. It is not recurring availability. Existing seed rows and the local refresher use 08:00–19:00.
- Household matching already covers pets, food context, utensil preferences, languages, allergy constraints, religious food restrictions, worker constraints, heavy lifting, disallowed chemicals, required equipment, and accessibility capabilities. It must be reused for seeded compatibility and all tier/favourite matching.
- Matching converts the visit to `Asia/Kolkata`, requires the whole visit to fit a date-specific availability row, and intersects active profiles, enabled skills, and availability. It then applies compatibility and Haversine proximity. A candidate must be within both the request maximum (currently 15 km) and the worker's own service radius.

### Existing tiers and pricing

- Worker Service already defines `STANDARD`, `VETERAN`, and `EXPERT` in `WorkerTier`; Booking Service already stores the assigned tier as a string on each `slot_hold`.
- Tier is currently a mutable persisted `worker_profile.tier`; it is not derived from tenure and cannot advance automatically.
- Existing hard-coded per-visit surcharges already equal the requested amounts: Standard `0`, Veteran `3000`, and Expert `5000` minor units (₹0/₹30/₹50). They are currently enum constants rather than configuration.
- Worker matching calculates `basePricePerHourMinor * durationMinutes / 60 + tier surcharge`. Booking Service persists only `quoted_price_minor` and `assigned_worker_tier`; it does not persist the surcharge separately. The visit quote is already the authority for booking totals, payment allocation, cancellation, and per-visit refund ceilings.
- Booking Service currently makes a Veteran visit free of its surcharge whenever Plus is active and `booking.plus-veteran-upgrade-free` is enabled. This applies broadly to a Veteran match, not only to a fallback upgrade, so the implementation must preserve only the intended existing perk while making chosen-tier pricing honest.

### Existing Plus and favourite behaviour

- User Service owns Plus. `user_profile` caches `plus_active` and `plus_expires_at`; `plus_subscription` records paid/granted entitlement windows; `plus_pass` records discount passes and their `AVAILABLE`, `HELD`, or `USED` lifecycle.
- A signed Wallet purchase event activates a configured plan. The default monthly plan costs 19,900 minor units, lasts 30 days, creates four passes, and gives each pass a 3,000-minor-unit discount. A pass is reserved during an atomic booking hold, consumed on confirmation, released on failed/expired holds, restored after platform cancellation, and reconciled from durable booking events.
- Existing Plus perks used by booking are: discounted booking passes, favourite-first weighting/continuity in worker matching, a free Veteran fallback upgrade when configured, and a 50% reduction of configured cancellation fees. Plus does not change who may create a multi-day booking.
- `favourite_worker` already belongs to User Service and has `(user_id, worker_id)` identity, a unique per-user position named `priority`, and a database check limiting positions to 1–3. Add/remove operations lock the user and fill the first free position; the limit is hard-coded to three in service logic and schema.
- Existing favourite operations are nested under the household endpoint and return a complete `HouseholdSnapshot`. There is no dedicated list response, suggestions endpoint, public worker-profile endpoint, completed-visit eligibility check, seeded-default marker, or durable tombstone for a removed seed favourite.
- Add, remove, household read, and booking use are not currently Plus-gated. Stored favourites flow to Booking Service through the internal household snapshot. Matching boosts an available favourite (more strongly for Plus), tries same-tier nearest fallback for an unavailable selected favourite, then can use the configured Plus Veteran upgrade. The new feature must gate favourite capabilities without deleting stored rows when entitlement lapses.
- The current row lock serializes normal adds for one customer, while the `(user_id, priority)` uniqueness constraint protects occupied positions. The configurable maximum and concurrency-safe domain error still need to be implemented.

### Existing ratings and ranking

- Worker Service already exposes `POST /api/v1/ratings/workers/{workerId}` with `bookingId`, `visitIndex`, `stars`, and optional comment.
- It asks Booking Service for an internal rating context, verifies the authenticated customer owns the booking, and permits only the assigned worker of a completed visit.
- `worker_rating` has one row per `(booking_id, visit_index)`. The existing repeat call is idempotent only when every value is identical; a different value is rejected permanently. There is no configurable update window or update timestamp.
- `rating_sum` and `rating_count` already live on `worker_profile` and are incremented under a pessimistic worker lock in the same transaction as the rating row and outbox write.
- A `WorkerRated` outbox event already exists, but its payload currently includes `stars`; the target contract requires identifiers only.
- Matching currently ranks by ordinary average rating plus distance (and favourite boost). There is no Bayesian rank configuration. Suggestions do not exist.

### Existing multi-day choices to reuse

- Booking Service accepts `ExpertPreference.ANY`, `FAVOURITE`, and `VETERAN`. `FAVOURITE` requires `favouriteWorkerId` and validates it against the customer's stored favourites.
- `FallbackPolicy` is `AUTO_ASSIGN`, `SKIP_AND_REFUND`, or `ASK_FIRST`; `ASK_FIRST` is deliberately rejected because no durable customer-decision workflow exists.
- Per-visit assignment types already are `PREFERRED`, `FALLBACK_SAME_TIER`, `FALLBACK_PLUS_UPGRADE`, and `UNASSIGNED`. They are persisted on `slot_hold` and exposed in booking responses.
- Visits already have independent durations, workers, assigned-tier strings, assignment types, quotes, cancellation/refund state, and household snapshots. Atomic holds, payment, refunds, and dropout recovery must be extended rather than replaced.
- The request does not currently carry a chosen `STANDARD`/`VETERAN`/`EXPERT` tier. The `requiredTier` field exists only on the internal Worker Service matching request, mainly for reassignment. Availability returns only one compatible-worker count and lowest price per visit, not a per-tier breakdown.

### Existing local seed, production guard, and login behaviour

- Every service defaults to the `local` Spring profile. Existing prototype records are inserted by ordinary Flyway migrations, including two real-format Indian worker phones and one real-format customer phone; these are not controlled by an opt-in seed flag.
- `LocalWorkerSeedInitializer` and `LocalBookingSeedInitializer` are `@Profile("local")` application runners. They currently refresh only the two stable prototype workers: Worker Service adds missing date-specific availability, and Booking Service adds matching capacity rows. There is no `dev-seed.enabled` guard, scheduled daily refresh, ten-worker dataset, or cross-service seed coordination.
- The shared production guard is activated only under the `production` profile. It validates each service's required properties and rejects missing values, local-development values, and non-production adapters. It does not yet reject dev seed.
- Customer OTP always uses the configured verification provider. After a successful code check, an existing `ACTIVE` `user_profile` receives a customer JWT immediately; a missing profile receives `REGISTRATION_REQUIRED`; suspended/deleted profiles cannot authenticate. This is the path the demo customer must reuse—there must be no OTP bypass.
- User Service already resolves users by normalized phone internally through `UserProfileRepository.findByPhoneE164`, but the authenticated internal HTTP API exposes customer contact lookup only by user UUID. There is no internal phone-to-customer lookup endpoint yet, so other services cannot safely resolve the configured demo phone without adding an authenticated internal contract and retry/lazy handling.

## Implementation gaps and invariants

The implementation must add query-time tenure tiers, configured thresholds/surcharges and Bayesian ranking; snapshot tier and surcharge per visit; chosen-tier and per-tier availability contracts; Plus-gated favourite list/add/remove/use/suggestions; completed-visit eligibility; public phone-free worker profiles; rating updates within a bounded window; and an opt-in, production-refused, cross-service-safe seed.

It must retain the existing date-specific availability model, household compatibility dimensions, 08:00–19:00 service day, proximity checks, Plus pass saga, cancellation discount, multi-day visit model, fallback policies, assignment types, signed-webhook payment truth, normal OTP authentication, service database ownership, internal service authentication, and additive-only Flyway history.

## Checkpoint status

Step 0 is complete when this audit is committed. Later sections will be updated with exact API contracts, migrations, configuration, seed/reset instructions, errors, tests, and final verification as the numbered implementation checkpoints land.
