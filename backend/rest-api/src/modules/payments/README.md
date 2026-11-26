# Payments

Taking money for Ongero Plus, through more than one gateway.

## Shape

```
PaymentsController          POST /payments/pay              → the default gateway
                            GET  /payments/:ref/status      → reconcile a lost webhook
PalplussWebhookController   POST /payments/palpluss/webhook → signed, public
MpesaController             POST /mpesa/{pay,query,callback,timeout}  (legacy, direct Daraja)

PaymentGateway (interface)
  ├── PalplussGateway   api.palpluss.com/v1   — signed callbacks
  └── DarajaGateway     api.safaricom.co.ke   — unsigned callbacks

PaymentsService   what a payment MEANS. One implementation, every gateway.
```

A gateway knows how to speak to one vendor and nothing else. Whether a result should
grant a subscription — whether we issued that id, whether the attempt is still pending,
whether the amount paid is the amount we asked for — lives in `PaymentsService` and is
identical for all of them. That split is the point: those checks are the ones that
protect money, and they should not be rewritten per vendor.

`PaymentsModule` imports `MpesaModule` (an HTTP client and nothing else) and never the
other way round, which is why the Daraja controller lives here rather than next to the
client it calls.

## Which gateway

`PAYMENTS_PROVIDER=palpluss` (the default) or `daraja`. The person paying sees the same
M-Pesa prompt either way, so there is no provider in the request body; the choice is a
deployment decision, recorded on `payments.provider` so a result can only be matched
against a row its own gateway created.

## Setting up PalPluss

1. Register at https://console.palpluss.com and complete KYC. Keys work before KYC; live
   transactions do not.
2. **API Keys → Create new Key.** Choose Test to start — test keys use the same URL and
   move simulated money. Set `PALPLUSS_API_KEY`. The key is shown once.
3. Set `PALPLUSS_CALLBACK_URL` to a **public HTTPS** URL ending
   `/api/v1/payments/palpluss/webhook`. PalPluss cannot reach localhost or any private
   address; use ngrok in development.
4. **Webhooks → Add domain**, entering the host from step 3. Copy the `whsec_…` secret
   into `PALPLUSS_WEBHOOK_SECRET`. Do this. See below.
5. Top up the service wallet. The per-transaction fee is taken from it at request time
   and an empty wallet returns 402, which this service reports as
   "temporarily unavailable" rather than telling the customer they have no money.

## Why step 4 matters

The webhook endpoint grants paid access and cannot be authenticated — PalPluss has no
credential of ours to present. Three things stand between it and a free subscription:

1. the Standard Webhooks signature, **when a secret is configured**;
2. the correlation id having been issued by us, through this gateway;
3. the amount paid matching the amount we asked for.

With `PALPLUSS_WEBHOOK_SECRET` unset, PalPluss still delivers — unsigned, to any domain
not registered for signing — and only 2 and 3 apply. That is the same position the
Daraja integration is permanently in, and it is not nothing: an attacker needs a
transaction UUID issued seconds earlier to a real payer. But it is strictly worse, it is
logged as an error on every delivery while it lasts, and the fix is a console setting.

`test/palpluss-webhook.db-check.ts` demonstrates the difference. Disabling the signature
check turns 11 of its 20 assertions red, including an unsigned forgery successfully
granting a subscription.

## When a webhook never arrives

PalPluss gives up after five attempts (the first plus retries at 10s, 30s, 1m, 2m).
`GET /payments/:ref/status` asks the gateway directly when the stored row is still
pending, and applies the answer through `applyCallback` — the same amount check, the
same atomic claim, the same idempotency. It is a second route to the same decision, not
a weaker one.

## Field limits worth knowing

`accountReference` is capped at 12 characters and `transactionDesc` at 13. A user id is
a 36-character UUID, so it cannot be the reference; `generateReference()` produces
`ONG` plus 9 characters instead. The prompt text is the constant `Ongero Plus` (11)
because `Ongero monthly` is 14 — PalPluss rejects that outright and Daraja silently
truncates it to `Ongero monthl` on a real person's phone.

## Two places the vendor's own documents disagree

Recorded because the code deliberately does not take a side:

- **Auth.** The OpenAPI `securitySchemes` description says to send the key unencoded
  (`Authorization: Basic YOUR_API_KEY`). The authentication guide, and all five of its
  code samples, say `base64(key + ":")` and call out the trailing colon. We follow the
  samples.
- **Transaction status.** The OpenAPI enum is
  `PENDING/PROCESSING/SUCCESS/FAILED/REVERSED/CANCELLED`; the webhook guide lists
  `SUCCESS/FAILED/CANCELLED/EXPIRED`. We do not switch exhaustively over either.
  `SUCCESS` succeeds, `PENDING`/`PROCESSING` are in flight, and everything else —
  including a value neither document mentions — is a failure.

## Not done

- No live call has ever been made against PalPluss. Everything here is verified against
  their published OpenAPI document and guides, unit tests, and an end-to-end run over
  real HTTP against real PostgreSQL with signatures generated by an independent
  implementation. A `pp_test_` key would close the last gap.
- Nothing in the Android app calls `/payments/pay`. There is no REST client.
- No refunds, no B2C payouts, no grace period, no dunning.
- `test/palpluss-webhook.db-check.ts` needs a database and so is not in CI.
