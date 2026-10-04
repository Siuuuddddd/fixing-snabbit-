# Android-to-backend API gaps

This list records gaps found by reading the current controllers, DTOs, gateway rules, events, and tests. It does not claim missing UI is a backend gap.

## Blocking or high priority

| Gap | Affected screens | Why it matters |
|---|---|---|
| No refresh-token endpoint or refresh-token contract | Login, every authenticated screen | Access tokens expire. The Android authenticator safely clears the session after a 401 instead of inventing refresh behavior. |
| No role-discovery or combined login endpoint | Signup/login role choice | Customer and worker OTP endpoints are separate and only allow active pre-seeded accounts. Registration/onboarding is not implemented. |
| No customer profile read/update endpoint | Profile | Addresses, household, Plus, favourites, and ratings exist, but a general profile contract does not. |
| No customer booking-list endpoint | My bookings | Individual booking lookup exists; list/filter/pagination for a customer is absent. |
| No wallet ledger/history endpoint | Wallet | Wallet summary and top-up creation exist, but the transaction list shown by the design cannot be fetched. |
| No top-up status/read endpoint | Add money | Creation returns the provider order, and the webhook credits later; the app cannot authoritatively poll a pending order. |
| No worker earnings endpoint | Worker earnings | The board requires totals and payout history that are not exposed. |

## Important follow-ups

| Gap | Affected screens | Notes |
|---|---|---|
| Worker registration/profile/onboarding APIs | Worker login/profile | Worker OTP is login for active workers, not self-registration. |
| Public worker detail/favourite presentation API | Home, assigned worker, favourites | Matching and favourite IDs exist, but the mobile display contract is incomplete. |
| Notification real-time stream | Notifications | History/device/read APIs exist; there is no notification SSE endpoint. |
| Worker schedule aggregate | Worker schedule | Date availability exists, but not the calendar/job aggregate implied by the screen. |
| Support ticket history and attachment contract | Help and support | Live message creation/streaming exists; richer inbox and media contracts need confirmation. |
| Payment hand-off contract validation | Booking payment, Add money | Razorpay order IDs are returned in existing flows, but the exact mobile SDK hand-off/error/status contract needs an end-to-end test. |
| Maps/serviceability UI contract | Address, live tracking | Address create/list/delete is implemented with consented device coordinates and backend serviceability verification. Provider autocomplete, map rendering, and route-display contracts remain undefined. |
| Pagination standards | Bookings, notifications, ledger, offers | Existing collections are not consistently cursor/page based. |

## Confirmed backend behavior the app must respect

- Customer and worker OTP request endpoints return a generic response for unknown or inactive numbers; the UI must not reveal account existence.
- Customer and worker JWTs carry different roles and are enforced at the gateway.
- The implemented Android login uses the product flavor to select the separate customer or worker OTP endpoints; it does not attempt role discovery or registration.
- Successful OTP verification stores only the returned access token in encrypted storage. Relaunch auto-login uses that session, while an unrecoverable 401 clears it because no refresh contract exists.
- The JWT subject is the customer UUID used by Android for owned address paths. Address creation is the available serviceability check; there is no separate client-facing serviceability endpoint.
- Wallet top-ups are credited only after a verified Razorpay webhook. The Android SDK result is never treated as payment truth.
- The backend uses RFC 7807-style `ProblemDetail` responses. Screen error mapping should use HTTP status plus `detail`, not exception class names.
- Money amounts use integer minor units.
