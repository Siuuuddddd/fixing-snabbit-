# Wallet and payment API

All monetary values are integer paise. Currency is `INR` in the prototype. Customer charges and top-ups use whole-rupee increments and must be at least ₹1, which keeps every possible split-payment remainder and later partial refund valid for Razorpay.

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

Every top-up, Plus purchase and booking payment uses a deterministic Razorpay receipt containing the complete internal UUID in 40 characters or fewer. Before creating an order, Wallet searches Razorpay for an exact receipt match. If order creation returns an ambiguous error, Wallet searches again and attaches the recovered order instead of creating a duplicate. Any failed create or reconciliation leaves the local row in `CREATING_ORDER`, because an immediately empty provider lookup is not sufficient proof that a timed-out create failed. The same idempotent request can therefore recover it safely; booking expiry or cancellation still releases any wallet reservation.

## Internal routes

- `GET /internal/v1/bookings/{bookingId}/payment-quote` — authoritative customer, lifecycle state, hold expiry and server-calculated total.
- `POST /internal/v1/wallets/{walletId}/booking-payments` — re-read the authoritative Booking quote, verify wallet ownership, atomically reserve available wallet funds and create a Razorpay order for the remainder.
- `POST /internal/v1/wallets/{walletId}/plus-purchases` — verify wallet ownership and create an idempotent Razorpay order from User Service's authoritative Plus plan terms.
- `POST /internal/v1/wallets/booking-payments/{paymentId}/cancel` — release a pending reservation. A later provider capture is automatically queued for a compensating refund.
- `POST /internal/v1/wallets/booking-payments/{paymentId}/refunds` — full refund with an idempotency key.

These routes require `X-Service-Token` and are intentionally absent from customer API Gateway routes. See [authentication.md](authentication.md).

The Wallet request contains `bookingId` and `useWallet`, never an amount. Wallet accepts only an `AWAITING_PAYMENT` booking with an unexpired hold, a positive INR quote and a wallet owned by the booking customer. Booking and Wallet both check these rules so neither an Android payload nor a mistaken internal caller can choose the charged total.

Plus purchase rows are separate from wallet top-ups and never credit the wallet ledger. A matching signed capture emits `PlusSubscriptionPurchased`; User Service alone converts that fact into membership and passes.

## Split payment behavior

For a ₹150 total with ₹100 available:

1. Reserve ₹100 from the wallet without writing a debit.
2. Create a Razorpay order for ₹50 outside the database transaction.
3. After a verified ₹50 capture, mark the reservation captured and append a ₹100 debit.
4. Emit `WalletPaymentSucceeded`; Booking may then confirm its held visits.
5. If the reservation expired or was cancelled before capture, queue a ₹50 provider refund instead of completing the booking.

## Refund safety

Refund requests are stored before calling Razorpay. A short transaction claims one refund as `PROCESSING`, so concurrent app instances cannot intentionally submit it twice. The durable refund UUID is sent as Razorpay's `X-Refund-Idempotency` key and as an audit note, making a retry safe even if Razorpay succeeds immediately before the Wallet process crashes. A five-minute processing lease lets the scheduler reclaim abandoned work; ordinary provider failures return it to `CREATING` immediately. Wallet portions are credited only after any required online refund succeeds.

Confirmed customer cancellations can request a partial refund only through the trusted `BookingCustomerCancelled` event. Wallet verifies that the requested amount is between zero and the captured total, retains the cancellation fee from the online portion first, then refunds the remainder to the original online and wallet sources. Platform cancellations use the same path with the full captured total. `totalSpentMinor` is derived from completed booking payments minus completed refunds, so a partial refund leaves only the retained fee as spend.

Finance operators and administrators can inspect the latest 100 refund records through
`GET /api/v1/staff/finance/refunds`, optionally filtering by `status`. An existing queued
refund can be retried with `POST /api/v1/staff/finance/refunds/{refundId}/retry`; retrying an
already completed refund is an idempotent no-op.
The retry request accepts no amount, payment ID or wallet ID: it reuses the immutable
refund record and the original provider payment. Every attempt stores the authenticated
staff ID and its outcome in `finance_refund_action`. The audit transaction finishes before
the provider call, so no database transaction remains open while Razorpay is contacted.

## Razorpay configuration

Local development defaults to fake provider mode. Real mode requires environment variables:

```text
RAZORPAY_MODE=real
RAZORPAY_KEY_ID=...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=...
```

The webhook signature is calculated over the exact raw request bytes and compared in constant time. Client-side Android or UPI success responses are never accepted as proof of payment.
