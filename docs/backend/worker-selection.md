# Booking-time worker selection

## Step 0 audit

This document records the backend behaviour that existed before booking-time worker selection was added. It is based on the current implementation, the Android household-preference screen, and the local databases on 2026-10-14. Later sections will be updated as the feature is implemented.

### Prerequisite feature set

The preceding backend work is present and committed:

- Worker tiers are derived at query time from `joined_at`, with configurable Standard/Veteran/Expert tenure thresholds.
- Per-visit tier surcharges are configured as 0/3000/5000 minor units and snapshotted with the visit quote.
- Plus-gated favourites retain positions 1–3, enforce a concurrent maximum of three, and expose phone-free public worker profiles and ranked suggestions.
- Completed-visit ratings update worker aggregates transactionally and matching uses the configured Bayesian rank score.
- The opt-in local/dev seed defines ten stable worker identifiers and an optional active demo Plus customer whose default favourites are Savita, Vineeta, and Deepika.

The exact existing contracts are documented in `worker-tiers-favourites-api.md`.

### Current booking and offer order

1. `POST /api/v1/bookings/availability` loads the verified address and household snapshot, calls Worker Service matching for every visit, and returns availability without persisting a booking.
2. `POST /api/v1/bookings/match-and-hold` repeats authoritative matching, reserves an optional Plus pass, and creates one `booking`, one `slot_hold`, and one targeted `worker_offer` per visit in a local transaction. The new booking starts as `AWAITING_WORKER`.
3. Each offer is `OFFERED` for `booking.worker-offer-duration`, currently `PT1M`. `WorkerOffersCreated` is appended to the booking outbox. The Worker app reads the offer through `GET /api/v1/worker/bookings/offers`.
4. The assigned worker accepts through `POST /api/v1/worker/bookings/{bookingId}/offers/{offerId}/accept` or rejects through the sibling `reject` route. Both routes verify the authenticated worker owns the offer and that it is still open.
5. When every visit offer is accepted, the booking moves to `AWAITING_PAYMENT` and emits `BookingAwaitingPayment`. A rejection moves the booking to `WORKER_UNAVAILABLE`, releases every hold, expires the other open offers, and emits `WorkerOfferRejected`.
6. The expiry scheduler treats an unaccepted expired offer the same way: it marks the booking `WORKER_UNAVAILABLE`, releases all holds, expires remaining offers, and emits `WorkerOfferExpired`.
7. `POST /api/v1/bookings/{bookingId}/payments` is therefore allowed only after all offers have been accepted. It resolves the home contact, extends holds within the configured cap, and creates the Wallet payment.
8. Only the signed Wallet success event calls `confirmPayment`. Confirmation changes the booking to `CONFIRMED`, confirms every hold, initializes each visit as `ASSIGNED`, and emits `BookingConfirmed` and assignment events.

The Customer Android `FindingWorkerScreen` renders **Finding your professional** whenever the returned booking status is `AWAITING_WORKER`; it is a UI label for the offer-acceptance wait, not a separate backend state.

### Why the local customer reaches “No professional available”

The running Booking database shows five historical bookings whose offers all reached `EXPIRED`; the latest booking `781aea35-0bf5-3a8d-8440-4d7538002e9c` matched worker `10000000-0000-0000-0000-000000000001`, created an offer, then changed to `WORKER_UNAVAILABLE` when that offer expired. Its hold was released. The three-visit example likewise created three offers and all three expired.

Therefore matching itself is succeeding. The failure is that a targeted offer still requires a Worker app acceptance, while the user can run only the Customer app. The current local `.env` also has `dev-seed.enabled` unset, so the new ten-worker dataset is not currently materialized in the running Worker database; disabling the seed is intentionally non-destructive, but this database contains zero `60000000-...` seed profiles at audit time.

### Household and worker preference vocabulary

User Service owns a single `household_preference` per customer. Booking Service fetches it once, converts it to `BookingRequirements`, and snapshots the complete object on the booking and every visit. Worker Service owns one `worker_compatibility` row per worker. Matching is case-insensitive.

| Household category | Customer vocabulary | Worker field and current rule |
|---|---|---|
| Pets at home | boolean plus optional pet details | `acceptsPets`; required only when `hasPets` is true. Pet details are informational. |
| Food preference | `VEGETARIAN`, `NON_VEGETARIAN`, `JAIN` | `acceptedFoodContexts`; the selected value must be present. The current ten-worker seed is missing `JAIN`. |
| Preferred languages | `Hindi`, `Marathi`, `English` | `languages`; when preferences are present, at least one must overlap because these are alternatives, not cumulative requirements. |
| Utensil preference | `SEPARATE_UTENSILS` (the backend also understands `SHARED_UTENSILS`) | `supportedUtensilPreferences`; every requested value must be present. |
| Allergies | `DUST`, `FRAGRANCE` (the backend also understands `PET_DANDER`) | `supportedAllergyConstraints`; every requested trigger must be supported. |
| Accessibility needs | `STEP_FREE_ACCESS`, `HEARING_SUPPORT` (existing fixtures also use `ELDER_SUPPORT`, `MOBILITY_SUPPORT`) | `accessibilityCapabilities`; every requested value must be present. The current ten-worker seed is missing `STEP_FREE_ACCESS`. |
| Equipment available/required | `MOP`, `VACUUM` (existing fixtures also use `STEAM_CLEANER`, `LADDER`) | `availableEquipment`; every requested value must be present. |
| Professional requirements | `MASK_REQUIRED`, `SHOE_COVERS` | `supportedWorkerConstraints`; every requested value must be present. Existing fixtures also use `FEMALE_WORKER`; the current ten-worker seed is missing `SHOE_COVERS`. |
| Heavy lifting | boolean | `canHeavyLift`; must be true when requested. |
| Disallowed chemicals | existing values include `BLEACH`, `AMMONIA`, `FRAGRANCE` | `avoidedChemicals`; every customer-disallowed chemical must be present in the worker's avoidance set. |
| Religious/food restrictions | existing values include `VEGETARIAN_ONLY`, `HALAL_ONLY`, `NO_BEEF`, `NO_PORK` | `acceptedReligiousFoodRestrictions`; every requested restriction must be present. |

The existing columns already mirror all household categories with list-valued vocabularies; missing vocabulary values are data/validation gaps rather than a reason to add parallel preference tables.

### Current matching pipeline

For each requested visit, Booking Service sends the service code, authoritative catalogue base price, visit start and duration, verified address coordinates, household snapshot, Plus/favourite context, tier request, and fallback policy to Worker Service.

Worker Service then:

1. loads `ACTIVE` profiles;
2. intersects them with an enabled skill for the requested service;
3. requires a date-specific `AVAILABLE` row whose start/end fully contains the visit;
4. applies the household compatibility checks listed above;
5. calculates Haversine distance from the verified address to the worker's base location;
6. excludes a candidate outside either the request maximum (currently 15 km) or the worker's own `service_radius_km`;
7. derives the current tenure tier and configured per-visit surcharge;
8. ranks candidates with favourite weighting, Bayesian rating score, distance, then stable worker ID, while exact-tier/favourite fallback rules may further constrain the list.

The current match response rounds distance to two decimal places. Booking-time selection requires a shared compatibility result and a customer-facing distance rounded to 0.1 km so the options list and final validation cannot diverge.

### Gaps to implement

- Extract compatibility into one pure, tested policy used by both ordinary matching and worker-option validation.
- Seed the missing `JAIN`, `STEP_FREE_ACCESS`, and `SHOE_COVERS` values for all ten dev workers.
- Add owner-only held-booking worker options: up to three favourites with explicit availability/compatibility reasons plus two distinct full-match Veterans.
- Persist and revalidate an optional worker selection, re-snapshot tier/price per visit, and auto-select at payment start when the customer does not choose.
- Continue using the existing targeted offer and accept path. Real workers remain manual; only stable dev-seed workers may auto-accept under guarded local/dev configuration.
- Because arrival OTP is implemented, guarded dev auto-progress may move an assigned seeded visit to `ARRIVED` and create the normal challenge, but it must stop there and never bypass OTP verification.
