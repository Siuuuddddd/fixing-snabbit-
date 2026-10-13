# Worker tiers, favourites, ratings, and development seed API

All money values are integer minor units (paise). Public customer routes require the normal customer bearer token; the gateway supplies `X-Authenticated-User-Id`. Clients never send a surcharge, rating aggregate, rank score, or authoritative tier price.

## Booking tier selection and quotes

`POST /api/v1/bookings/availability` and `POST /api/v1/bookings/match-and-hold` retain their existing `MatchAndHoldRequest` and add:

```json
{
  "workerTier": "STANDARD | VETERAN | EXPERT",
  "expertPreference": "ANY | VETERAN | FAVOURITE",
  "favouriteWorkerId": "uuid-or-null"
}
```

`workerTier` is optional for backward compatibility. For `FAVOURITE`, `favouriteWorkerId` is required and the favourite's server-derived tier overrides `workerTier`. Every visit has its own `durationHours`; matching requires exactly the selected tier. The availability response adds this shape to every visit:

```json
{
  "startsAt": "2026-10-14T04:00:00Z",
  "durationMinutes": 120,
  "available": true,
  "compatibleWorkerCount": 3,
  "lowestPriceMinor": 41000,
  "selectedTier": "EXPERT",
  "tierOptions": [
    {"tier":"STANDARD","available":true,"compatibleWorkerCount":5,"lowestPriceMinor":36000},
    {"tier":"VETERAN","available":true,"compatibleWorkerCount":2,"lowestPriceMinor":39000},
    {"tier":"EXPERT","available":true,"compatibleWorkerCount":3,"lowestPriceMinor":41000}
  ]
}
```

The held visit snapshots `assignedWorkerTier`, `tierSurchargeMinor`, `quotedPriceMinor`, and `assignmentType`. Standard/Veteran/Expert add ₹0/₹30/₹50 per visit by default. Payment allocation, cancellation, and refunds use the stored quote; a later tenure boundary cannot reprice an existing booking. A free Plus Veteran surcharge applies only to `FALLBACK_PLUS_UPGRADE`.

## Plus favourites

Routes:

- `GET /api/v1/users/{userId}/favourites`
- `POST /api/v1/users/{userId}/favourites/{workerId}`
- `DELETE /api/v1/users/{userId}/favourites/{workerId}`
- `GET /api/v1/users/{userId}/favourites/suggestions`

List/add/suggestions and favourite booking require active Plus. Remove remains available after Plus lapses. A list/add response is:

```json
{
  "userId": "uuid",
  "maxCount": 3,
  "favourites": [{
    "position": 1,
    "worker": {
      "workerId": "uuid",
      "firstName": "Savita",
      "tier": "EXPERT",
      "averageRating": 4.9,
      "ratingCount": 140,
      "completedJobs": 160,
      "tenureMonths": 12,
      "rankScore": 4.85,
      "customerRating": 5
    }
  }]
}
```

No worker/favourite response contains a phone number. Suggestions contain the same worker shape, exclude existing favourites, require a completed visit by default, and sort by the customer's latest rating followed by Bayesian rank. Seeded defaults are explicitly eligibility-exempt. Adds lock the customer row, while unique positions and the database `1..3` check protect concurrent requests.

`GET /api/v1/workers/{workerId}/profile` returns the same public worker fields without `customerRating`. It never returns authentication or contact data.

## Ratings

`POST /api/v1/ratings/workers/{workerId}`:

```json
{
  "bookingId": "uuid",
  "visitIndex": 0,
  "stars": 5,
  "comment": "Optional, at most 500 characters"
}
```

Only the booking owner can rate the assigned worker after that visit is `COMPLETED`. One row exists per booking visit. An identical replay is idempotent. A different rating updates the row and adjusts `rating_sum` by the star delta during `WORKER_RATING_UPDATE_WINDOW` (default `PT24H`); it is rejected afterward. The response includes `ratingId`, booking/visit/worker/customer identifiers, stars, comment, and `workerAverageRating`. `WorkerRated` outbox payloads contain identifiers only.

Worker rank is `(C × m + rating_sum) / (C + rating_count)`, configured by `WORKER_RANK_PRIOR_MEAN` (default `4.5`) and `WORKER_RANK_PRIOR_WEIGHT` (default `20`). Rank orders matches within a tier, same-tier fallback ties, and suggestions; it never changes tenure tier.

## Error contracts

Problem Details retain `status`, `detail`, and the following extension:

| Situation | HTTP | Extension |
|---|---:|---|
| Plus missing/expired for favourite capability | 403 | `code: PLUS_REQUIRED` |
| Fourth favourite | 409 | `code: FAVOURITE_LIMIT_REACHED` |
| No completed visit and not seed-exempt | 409 | `code: NOT_ELIGIBLE_FAVOURITE` |
| Rating caller is not owner, visit is incomplete, or worker differs | 403 | `errorCode: RATING_NOT_ALLOWED` |
| Stars outside 1–5 | 422 | `errorCode: RATING_INVALID` |
| Different rating after update window | 409 | `errorCode: RATING_UPDATE_WINDOW_EXPIRED` |
| Conflicting rating identity | 409 | `errorCode: RATING_REJECTED` |

## Development seed

The seed is disabled by default and exists only under the `local` or `dev` profile. Set these in the untracked local `.env`:

```text
DEV_SEED_ENABLED=true
DEV_SEED_DEMO_CUSTOMER_PHONE=<your verified +91 E.164 number>
DEV_SEED_DEMO_CUSTOMER_NAME=Demo Customer
DEV_SEED_WORKER_LOGIN_PHONE=<optional verified +91 E.164 number for Savita>
```

The remaining `DEV_SEED_*` variables in `.env.example` control the 10-day window, two-day margin, centre coordinates, radius, Plus validity, and renewal threshold. Never commit real values. Production startup fails when `dev-seed.enabled=true`.

Each startup and the daily refresh upsert ten stable workers: three Expert (Savita, Vineeta, Deepika), two Veteran (Sangeeta, Asha), and five Standard (Vaishali, Sana, Sailee, Shivani, Alisha). Join dates are re-anchored by configured calendar-month tenure, but initial rating aggregates are inserted once and never overwritten. All ten receive all four services, full compatibility fixtures, 08:00–19:00 date-specific availability, and Booking Service capacity for the booking window plus margin. Placeholder worker phones are deliberately invalid and cannot log in or receive SMS; only the optional Savita override can authenticate.

When the demo phone is set, User Service creates an `ACTIVE` customer only if absent and never overwrites an existing profile. Normal Twilio Verify login remains mandatory. The existing Plus activation mechanism grants/renews a 365-day entitlement with four ₹30 discount passes when fewer than 30 days remain. Savita, Vineeta, and Deepika are applied once as seed-exempt favourites; per-worker application markers prevent a removed favourite from returning. Startup retry, first favourite access, and the first booking household lookup can finish cross-service provisioning after services become reachable.

To disable, set `DEV_SEED_ENABLED=false` and restart. Existing fixture rows deliberately remain so disabling is non-destructive. For a full reset, stop the local stack and recreate only the local development database volumes, then restart with the flag enabled. Never use this reset procedure against shared or production data.
