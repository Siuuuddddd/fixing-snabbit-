# Product decisions

## Confirmed scope

- Launch market: Mumbai.
- Prototype data: customers and workers are seeded; APIs and persistence remain production-shaped.
- Services offered: complete home-service catalogue, represented as configurable catalogue data rather than code constants.
- Price: service + duration + worker-tier surcharge; no surge pricing initially.
- Worker tiers: Standard base price, Veteran + INR 30 per visit, Expert + INR 50 per visit.
- Booking types: instant, scheduled and multi-day.
- Multi-day UX: expose unavailable visits and let the customer replace them; final confirmation is atomic.
- Matching: automatic, with favourites prioritized when compatible and available.
- Job flow: hold capacity, offer job, worker accepts, collect payment, confirm booking.
- Payments: Razorpay; wallet balance can be combined with online payment.
- Platform failure: full refund if no worker can be provided.
- Plus: subscription with discounted passes, favourite continuity, priority matching and reduced cancellation fees.
- Authentication: Indian phone number and OTP.
- Roles: customer, worker, support agent, operations manager, administrator and finance/refund operator.
- Location: saved address + coordinates, consented live location and geofenced arrival (provisional radius: 200 m).
- Live updates: WebSockets with push-notification fallback.
- Notifications: push and SMS for critical events.
- Support: live chat in the first product scope.
- Compatibility: language, allergies, religious/food restrictions, accessibility, heavy lifting, cleaning chemicals and equipment requirements.
- Ratings: customers and workers rate each other.

## Booking rules

- Instant bookings are for the current day.
- Scheduled bookings cover today through the next seven days.
- Multi-day bookings contain 2–7 visits.
- Operating hours are 08:00–19:00 Asia/Kolkata.
- Visit duration is 1–4 hours and must be identical in customer and worker views.
- Capacity is locked briefly with PostgreSQL pessimistic locking, then represented as a five-minute `HELD` record.
- A database transaction is never held open during payment or worker response.

## Provisional decisions

These defaults unblock implementation and remain configurable:

- Worker offer timeout: 60 seconds per candidate.
- Slot hold lifetime: 5 minutes.
- Arrival geofence: 200 metres.
- Customer cancellation fees are configurable. Prototype defaults are ₹50 at least two hours before the earliest visit and ₹100 inside two hours, with a 50% Plus reduction. Cancellation is blocked after arrival/service activity; platform failure is always fully refunded.
- Public product name and non-reserved base package are still required. `com.snabbit.prototype` is temporary because Kotlin forbids application code in `kotlin.*`.
