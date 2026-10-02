# Mutual ratings

Ratings are permitted only after the relevant visit reaches `COMPLETED`. Both rating services read an authoritative `/internal/v1/bookings/{bookingId}/rating-context` snapshot before opening their local database transaction.

## Customer rates worker

`POST /api/v1/ratings/workers/{workerId}` accepts `bookingId`, `visitIndex`, one to five `stars`, and an optional 500-character comment. Worker Service verifies that the JWT customer owns the booking and that the requested worker completed that visit. It then locks the worker profile, stores one rating per booking visit and updates the worker's rating sum/count.

## Worker rates customer

`POST /api/v1/worker/ratings/customers/{customerId}` uses the same body. User Service verifies that the JWT worker completed the specified visit for that customer. It then locks the customer profile, stores one rating per booking visit and updates the customer's rating sum/count.

Identical retries return the original rating without changing aggregates. Reusing a booking visit with different rating details returns a conflict. Rating eligibility is checked over REST before the short local transaction; neither service reads Booking's database.
