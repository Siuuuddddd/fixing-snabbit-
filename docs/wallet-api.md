# Wallet and payment API

All monetary values are integer paise. Currency is `INR` in the prototype.

## Accounting invariant

```text
ledger balance = sum(CREDIT) - sum(DEBIT)
available balance = ledger balance - non-expired ACTIVE reservations
```

There is no mutable balance column. Ledger entries are append-only and carry a unique idempotency key. `totalSpentMinor` is the sum of booking debits and is intentionally labelled **Total spent**, not cash.

## Customer routes

- `GET /api/v1/wallets/{walletId}` — ledger, reserved, available and total-spent amounts.
- `POST /api/v1/wallets/{walletId}/top-ups` — create a server-side Razorpay order. Requires `Idempotency-Key`.
- `POST /api/v1/bookings/{bookingId}/payments` — Booking verifies the active hold and delegates split payment to Wallet. The body contains only `walletId` and `useWallet`; no client-supplied amount is accepted.

Creating a top-up does not credit the wallet. Credit occurs only after `/webhooks/razorpay` receives an HMAC-valid `payment.captured` event whose order and amount match the pending top-up.

## Internal routes

- `GET /internal/v1/bookings/{bookingId}/payment-quote` — authoritative customer, lifecycle state, hold expiry and server-calculated total.
- `POST /internal/v1/wallets/{walletId}/booking-payments` — re-read the authoritative Booking quote, verify wallet ownership, atomically reserve available wallet funds and create a Razorpay order for the remainder.
- `POST /internal/v1/wallets/booking-payments/{paymentId}/cancel` — release a pending reservation. A later provider capture is automatically queued for a compensating refund.
- `POST /internal/v1/wallets/booking-payments/{paymentId}/refunds` — full refund with an idempotency key.

These routes require `X-Service-Token` and are intentionally absent from customer API Gateway routes. See [authentication.md](authentication.md).

The Wallet request contains `bookingId` and `useWallet`, never an amount. Wallet accepts only an `AWAITING_PAYMENT` booking with an unexpired hold, a positive INR quote and a wallet owned by the booking customer. Booking and Wallet both check these rules so neither an Android payload nor a mistaken internal caller can choose the charged total.

## Split payment behavior

For a ₹150 total with ₹100 available:

1. Reserve ₹100 from the wallet without writing a debit.
2. Create a Razorpay order for ₹50 outside the database transaction.
3. After a verified ₹50 capture, mark the reservation captured and append a ₹100 debit.
4. Emit `WalletPaymentSucceeded`; Booking may then confirm its held visits.
5. If the reservation expired or was cancelled before capture, queue a ₹50 provider refund instead of completing the booking.

## Refund safety

Refund requests are stored before calling Razorpay. A short transaction claims one refund as `PROCESSING`, so concurrent app instances cannot intentionally submit it twice. Provider failures return it to `CREATING` for the retry scheduler. Wallet portions are credited only after any required online refund succeeds.

Confirmed customer cancellations can request a partial refund only through the trusted `BookingCustomerCancelled` event. Wallet verifies that the requested amount is between zero and the captured total, retains the cancellation fee from the online portion first, then refunds the remainder to the original online and wallet sources. Platform cancellations use the same path with the full captured total. `totalSpentMinor` is derived from completed booking payments minus completed refunds, so a partial refund leaves only the retained fee as spend.

## Razorpay configuration

Local development defaults to fake provider mode. Real mode requires environment variables:

```text
RAZORPAY_MODE=real
RAZORPAY_KEY_ID=...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=...
```

The webhook signature is calculated over the exact raw request bytes and compared in constant time. Client-side Android or UPI success responses are never accepted as proof of payment.
