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

Creating a top-up does not credit the wallet. Credit occurs only after `/webhooks/razorpay` receives an HMAC-valid `payment.captured` event whose order and amount match the pending top-up.

## Internal routes

- `POST /internal/v1/wallets/{walletId}/booking-payments` — atomically reserve available wallet funds and create a Razorpay order for the remainder.
- `POST /internal/v1/wallets/booking-payments/{paymentId}/cancel` — release a pending reservation. A later provider capture is automatically queued for a compensating refund.
- `POST /internal/v1/wallets/booking-payments/{paymentId}/refunds` — full refund with an idempotency key.

These routes must receive service identity before production. They are intentionally absent from customer API Gateway routes.

## Split payment behavior

For a ₹150 total with ₹100 available:

1. Reserve ₹100 from the wallet without writing a debit.
2. Create a Razorpay order for ₹50 outside the database transaction.
3. After a verified ₹50 capture, mark the reservation captured and append a ₹100 debit.
4. Emit `WalletPaymentSucceeded`; Booking may then confirm its held visits.
5. If the reservation expired or was cancelled before capture, queue a ₹50 provider refund instead of completing the booking.

## Refund safety

Refund requests are stored before calling Razorpay. A short transaction claims one refund as `PROCESSING`, so concurrent app instances cannot intentionally submit it twice. Provider failures return it to `CREATING` for the retry scheduler. Wallet portions are credited only after any required online refund succeeds.

## Razorpay configuration

Local development defaults to fake provider mode. Real mode requires environment variables:

```text
RAZORPAY_MODE=real
RAZORPAY_KEY_ID=...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=...
```

The webhook signature is calculated over the exact raw request bytes and compared in constant time. Client-side Android or UPI success responses are never accepted as proof of payment.
