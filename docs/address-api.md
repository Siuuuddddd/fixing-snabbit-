# Saved and verified service addresses

User Service owns customer addresses. Customer routes require a JWT subject matching `userId`:

- `GET /api/v1/users/{userId}/addresses`
- `POST /api/v1/users/{userId}/addresses`
- `DELETE /api/v1/users/{userId}/addresses/{addressId}`

Saving an address requires a Mumbai address and pincode plus a consented device-location sample captured within five minutes, no more than 30 seconds in the future and accurate to 100 metres. Coordinates must fall inside the configured prototype Mumbai service area. A customer can retain at most ten addresses.

Booking requests submit `addressId`, not latitude/longitude. Booking Service calls protected `GET /internal/v1/users/{userId}/addresses/{addressId}`; User Service verifies ownership and verification status. Booking then persists the address ID, label, line, pincode and coordinates as an immutable booking snapshot. Deleting the saved address later does not change an existing job.

Pending worker offers never disclose the address or coordinates. An assigned worker receives the snapshotted address and coordinates only inside the two-hour pre-visit window. Arrival still requires a fresh worker GPS point accurate to 100 metres and inside the 200-metre geofence.
