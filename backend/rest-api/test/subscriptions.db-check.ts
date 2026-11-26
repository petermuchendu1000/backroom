/**
 * End-to-end check of the subscription lapse sweep against a REAL PostgreSQL.
 *
 * Not part of CI: it needs a database. It is kept because it found three defects that
 * mocked unit tests could not — an entity column TypeORM refused to map, a misread
 * query return shape, and a sweep that silently downgraded nobody.
 *
 *   createdb backroom
 *   psql -d backroom -f backend/migrations/init.sql
 *   psql -d backroom -f backend/migrations/002_mpesa_payments.sql
 *   PGHOST=127.0.0.1 PGPORT=5432 PGUSER=postgres PGPASSWORD=... \
 *     npx ts-node test/subscriptions.db-check.ts
 */
import 'reflect-metadata';
import { DataSource } from 'typeorm';
import { Payment } from '../src/database/entities/payment.entity';
import { Subscription } from '../src/database/entities/subscription.entity';
import { SubscriptionTier, User } from '../src/database/entities/user.entity';
import { SubscriptionsService } from '../src/modules/subscriptions/subscriptions.service';

const ds = new DataSource({
  type: 'postgres', host: '127.0.0.1', port: 55432,
  username: 'postgres', password: 'pgpw', database: 'backroom',
  entities: [Payment, Subscription, User], synchronize: false, logging: false,
});

const HOUR = 3600_000;
let failures = 0;
function check(label: string, got: unknown, want: unknown) {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  if (!ok) failures++;
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label} :: got=${JSON.stringify(got)} want=${JSON.stringify(want)}`);
}

(async () => {
  await ds.initialize();
  const svc = new SubscriptionsService(ds.getRepository(Subscription), ds);
  const now = new Date();
  await ds.query('TRUNCATE subscriptions, payments, users CASCADE');

  const mk = async (tier: SubscriptionTier) =>
    (await ds.query(`INSERT INTO users (subscription_tier) VALUES ($1) RETURNING id`, [tier]))[0].id as string;
  const sub = async (userId: string, expiresAt: Date | null) =>
    ds.query(
      `INSERT INTO subscriptions (user_id, provider, provider_subscription_id, tier, started_at, expires_at, is_active)
       VALUES ($1,'mpesa',$2,'plus',$3,$4,true)`,
      [userId, 'ws_' + Math.random().toString(36).slice(2), new Date(now.getTime() - 40 * 24 * HOUR), expiresAt]);
  const tierOf = async (id: string) =>
    (await ds.query(`SELECT subscription_tier FROM users WHERE id=$1`, [id]))[0].subscription_tier;

  const lapsedUser = await mk(SubscriptionTier.PLUS);
  await sub(lapsedUser, new Date(now.getTime() - 24 * HOUR));
  const activeUser = await mk(SubscriptionTier.PLUS);
  await sub(activeUser, new Date(now.getTime() + 24 * HOUR));
  const renewedUser = await mk(SubscriptionTier.PLUS);
  await sub(renewedUser, new Date(now.getTime() - 2 * HOUR));
  await sub(renewedUser, new Date(now.getTime() + 30 * 24 * HOUR));

  console.log('\n--- derived entitlement, BEFORE any sweep has run ---');
  check('lapsed user already reads free', await svc.currentTier(lapsedUser), SubscriptionTier.FREE);
  check('paid-up user reads plus', await svc.currentTier(activeUser), SubscriptionTier.PLUS);
  check('renewed user reads plus', await svc.currentTier(renewedUser), SubscriptionTier.PLUS);
  check('cached users column is still stale', await tierOf(lapsedUser), 'plus');

  console.log('\n--- first sweep ---');
  check('counts', await svc.expireLapsed(), { lapsed: 2, downgraded: 1 });
  check('lapsed user downgraded', await tierOf(lapsedUser), 'free');
  check('paid-up user untouched', await tierOf(activeUser), 'plus');
  check('renewed-early user keeps plus', await tierOf(renewedUser), 'plus');

  console.log('\n--- second sweep must be a no-op ---');
  check('idempotent', await svc.expireLapsed(), { lapsed: 0, downgraded: 0 });
  check('unexpired rows still open',
    (await ds.query(`SELECT count(*)::int AS n FROM subscriptions WHERE is_active = true`))[0].n, 2);

  await ds.destroy();
  console.log(failures === 0 ? '\nALL CHECKS PASSED' : `\n${failures} CHECK(S) FAILED`);
  process.exit(failures === 0 ? 0 : 1);
})().catch((e) => { console.error(e); process.exit(1); });
