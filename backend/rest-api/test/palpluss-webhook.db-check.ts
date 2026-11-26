/**
 * End-to-end check of the PalPluss webhook, against a REAL PostgreSQL and over REAL
 * HTTP, because the thing being tested is a signature over the exact bytes on the wire.
 *
 * A unit test cannot prove this. It can hand the controller a Buffer it constructed
 * itself; it cannot prove that Nest's `rawBody: true` actually puts the received bytes
 * on the request, that the body parser has not already consumed them, or that the global
 * ValidationPipe does not interfere. Those are the ways this breaks in production while
 * every unit test still passes.
 *
 * Booting the whole AppModule also proves PaymentsModule -> MpesaModule introduces no
 * dependency cycle.
 *
 * Not part of CI: it needs a database.
 *
 *   createdb ongero_check
 *   psql -d ongero_check -f backend/migrations/init.sql \
 *     -f backend/migrations/002_mpesa_payments.sql \
 *     -f backend/migrations/003_payment_gateways.sql
 *   PGDATABASE=ongero_check npx ts-node test/palpluss-webhook.db-check.ts
 */
import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { createHmac } from 'node:crypto';
import { DataSource } from 'typeorm';
import { AppModule } from '../src/app.module';

const SECRET = process.env.PALPLUSS_WEBHOOK_SECRET as string;
const PORT = Number(process.env.CHECK_PORT ?? 8099);
const URL = `http://127.0.0.1:${PORT}/api/v1/payments/palpluss/webhook`;

let failures = 0;
function check(label: string, actual: unknown, expected: unknown): void {
  const ok = JSON.stringify(actual) === JSON.stringify(expected);
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}\n      expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}

/** Sign exactly as PalPluss does, over the bytes we are about to send. */
function post(bodyText: string, opts: { sign: boolean; skewSeconds?: number } = { sign: true }) {
  const id = `msg_${Math.random().toString(36).slice(2)}`;
  const timestamp = String(Math.floor(Date.now() / 1000) + (opts.skewSeconds ?? 0));
  const headers: Record<string, string> = { 'content-type': 'application/json' };

  if (opts.sign) {
    const key = Buffer.from(SECRET.replace(/^whsec_/, ''), 'base64');
    const sig = createHmac('sha256', key)
      .update(Buffer.concat([Buffer.from(`${id}.${timestamp}.`, 'utf8'), Buffer.from(bodyText, 'utf8')]))
      .digest('base64');
    Object.assign(headers, {
      'webhook-id': id,
      'webhook-timestamp': timestamp,
      'webhook-signature': `v1,${sig}`,
    });
  }

  return fetch(URL, { method: 'POST', headers, body: bodyText });
}

const successBody = (txId: string, amount: number) =>
  JSON.stringify({
    event: 'transaction.updated',
    event_type: 'transaction.success',
    transaction: {
      id: txId,
      type: 'STK',
      status: 'SUCCESS',
      amount,
      currency: 'KES',
      phone_number: '254712345678',
      external_reference: 'ONGB7K2M9XQ4',
      provider: 'm-pesa',
      mpesa_receipt: 'LGR019G3J2',
      result_code: '0',
      result_desc: 'The service request is processed successfully.',
    },
  });

(async () => {
  if (!SECRET) throw new Error('PALPLUSS_WEBHOOK_SECRET must be set for this check');

  // Mirrors main.ts exactly. rawBody is the whole point.
  const app = await NestFactory.create(AppModule, { rawBody: true, logger: ['error'] });
  app.setGlobalPrefix('api/v1');
  app.useGlobalPipes(
    new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }),
  );
  await app.listen(PORT, '127.0.0.1');
  console.log('1. container resolved and listening — no DI cycle through PaymentsModule\n');

  const ds = app.get(DataSource);
  await ds.query('TRUNCATE subscriptions, payments, users CASCADE');

  const uid: string = (
    await ds.query(
      `INSERT INTO users (subscription_tier, device_id) VALUES ('free','dev-pay') RETURNING id`,
    )
  )[0].id;

  const seed = async (txId: string, amountKes: number) => {
    await ds.query(
      `INSERT INTO payments (user_id, provider, checkout_request_id, reference, plan,
                             amount_kes, phone_number, status)
       VALUES ($1,'palpluss',$2,'ONGB7K2M9XQ4','monthly',$3,'254712345678','pending')`,
      [uid, txId, amountKes],
    );
  };

  const tierOf = async () =>
    (await ds.query(`SELECT subscription_tier FROM users WHERE id=$1`, [uid]))[0]
      .subscription_tier;
  const statusOf = async (txId: string) =>
    (await ds.query(`SELECT status FROM payments WHERE checkout_request_id=$1`, [txId]))[0]
      .status;
  const subCount = async () =>
    Number((await ds.query(`SELECT count(*)::int n FROM subscriptions WHERE user_id=$1`, [uid]))[0].n);

  // ---- 2. a forged webhook, unsigned ------------------------------------------------
  const forged = 'tx-forged-0000-0000-0000-000000000001';
  await seed(forged, 500);
  const r2 = await post(successBody(forged, 500), { sign: false });
  check('2. unsigned "payment succeeded" is refused', r2.status, 400);
  check('   …and the payment is untouched', await statusOf(forged), 'pending');
  check('   …and no subscription exists', await subCount(), 0);

  // ---- 3. a webhook whose body was edited after signing ------------------------------
  const tampered = 'tx-tampered-000-0000-0000-000000000002';
  await seed(tampered, 500);
  const signedText = successBody(tampered, 500);
  const key = Buffer.from(SECRET.replace(/^whsec_/, ''), 'base64');
  const id = 'msg_tamper';
  const ts = String(Math.floor(Date.now() / 1000));
  const sig = createHmac('sha256', key)
    .update(Buffer.concat([Buffer.from(`${id}.${ts}.`, 'utf8'), Buffer.from(signedText, 'utf8')]))
    .digest('base64');
  const r3 = await fetch(URL, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'webhook-id': id,
      'webhook-timestamp': ts,
      'webhook-signature': `v1,${sig}`,
    },
    body: successBody(tampered, 500).replace('"amount":500', '"amount":501'),
  });
  check('3. a body edited after signing is refused', r3.status, 400);
  check('   …and the payment is untouched', await statusOf(tampered), 'pending');

  // ---- 4. a correctly signed webhook for an id we never issued -----------------------
  const r4 = await post(successBody('tx-never-issued-0000-0000-00000003', 500));
  check('4. a signed webhook for an unknown id is acknowledged', r4.status, 200);
  check('   …and grants nothing', await subCount(), 0);

  // ---- 5. a correctly signed webhook paying less than we asked -----------------------
  const short = 'tx-short-00000-0000-0000-000000000004';
  await seed(short, 5000);
  const r5 = await post(successBody(short, 500));
  check('5. a signed webhook paying 500 against 5000 is acknowledged', r5.status, 200);
  check('   …but marks the payment failed', await statusOf(short), 'failed');
  check('   …and grants nothing', await subCount(), 0);

  // ---- 6. a replayed signature from outside the tolerance window ---------------------
  const stale = 'tx-stale-00000-0000-0000-000000000005';
  await seed(stale, 500);
  const r6 = await post(successBody(stale, 500), { sign: true, skewSeconds: -600 });
  check('6. a signature replayed 10 minutes later is refused', r6.status, 400);
  check('   …and the payment is untouched', await statusOf(stale), 'pending');

  // ---- 7. the real thing -------------------------------------------------------------
  const good = 'tx-genuine-000-0000-0000-000000000006';
  await seed(good, 500);
  const r7 = await post(successBody(good, 500));
  check('7. a genuine signed webhook is accepted', r7.status, 200);
  check('   …marks the payment succeeded', await statusOf(good), 'succeeded');
  check('   …records the M-Pesa receipt', (await ds.query(
    `SELECT mpesa_receipt_number m FROM payments WHERE checkout_request_id=$1`, [good]))[0].m,
    'LGR019G3J2');
  check('   …creates exactly one subscription', await subCount(), 1);
  check('   …under the palpluss provider', (await ds.query(
    `SELECT provider FROM subscriptions WHERE user_id=$1`, [uid]))[0].provider, 'palpluss');
  check('   …and upgrades the user', await tierOf(), 'plus');

  // ---- 8. the same delivery arriving again (PalPluss retries up to 5 times) ----------
  const r8 = await post(successBody(good, 500));
  check('8. a replay of the genuine webhook is acknowledged', r8.status, 200);
  check('   …and does NOT create a second subscription', await subCount(), 1);

  await app.close();
  console.log(`\n${failures === 0 ? 'PASS' : 'FAIL'}: ${failures} failed check(s)`);
  process.exit(failures === 0 ? 0 : 1);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
