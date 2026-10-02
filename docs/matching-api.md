# Household-aware worker matching

`POST /api/v1/bookings/match-and-hold` is coordinated by Booking Service. It requires an `Idempotency-Key`, reads the customer's household snapshot from User Service, requests candidates from Worker Service for each visit, and finally asks the existing Booking lifecycle to acquire real PostgreSQL slot holds. Exact retries replay the stored booking; the same customer/key cannot represent different booking details. A Worker Service availability result is advisory; the Booking database lock is the final concurrency boundary.

The request contains `serviceId` and an owned `addressId`, never client-selected price or free-form service coordinates. Booking resolves the active service code and hourly base price from its own catalog, then loads the verified address snapshot from User Service. Android therefore cannot mismatch an ID/code pair, choose a price, or substitute coordinates that do not belong to the authenticated household. `GET /api/v1/services` lists all active services. The prototype catalog includes house cleaning, dish washing, laundry and toilet cleaning.

## Matching rules

Candidates must be active, skilled for the requested service, available for the whole visit and within both the request radius and the worker's own service radius. Hard compatibility filters cover pets, food context, language, allergies, religious/food restrictions, utensils, accessibility, heavy lifting, worker constraints, avoided cleaning chemicals and required equipment.

Eligible workers are ranked by Plus/favourite preference, rating and distance. Plus multi-day bookings first look for one worker available on every visit and prefer the customer's favourite order; if continuity is impossible, the best candidate is chosen independently per visit. Tier pricing is base hourly price plus one per-visit surcharge: Standard ₹0, Veteran ₹30, Expert ₹50.

The selected per-visit prices are summed and persisted as `quotedTotalMinor` on the booking, so the server-calculated quote is not lost after matching.

Multi-day holds are all-or-nothing. If any visit has no compatible candidate, or the final Booking lock discovers that a selected slot was taken concurrently, the request fails without a partial booking.

## Requirement disclosure

The booking persists an immutable-at-creation requirement snapshot and includes it in `WorkerOffersCreated`. It contains pets, food and utensil preferences, languages, allergies, religious/food restrictions, accessibility needs, worker constraints, heavy-lifting needs, disallowed chemicals and required equipment. This keeps the worker's offer and the customer's booking based on the same facts.

## Example

```json
{
  "customerId": "40000000-0000-0000-0000-000000000001",
  "serviceId": "30000000-0000-0000-0000-000000000001",
  "bookingType": "MULTI_DAY",
  "durationHours": 2,
  "addressId": "42000000-0000-0000-0000-000000000001",
  "startsAt": [
    "2026-10-03T04:30:00Z",
    "2026-10-04T04:30:00Z"
  ]
}
```
