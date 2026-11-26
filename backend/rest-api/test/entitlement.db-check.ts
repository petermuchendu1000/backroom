/**
 * End-to-end check that entitlement reaches the JWT, against a REAL PostgreSQL.
 *
 * Boots the whole Nest container (which also proves there is no module cycle between
 * AuthModule and SubscriptionsModule), seeds a user whose cached
 * users.subscription_tier still says 'plus' while their period ended yesterday, and
 * asserts the minted and refreshed tokens both say 'free'.
 *
 * Not part of CI: it needs a database.
 *
 *   createdb backroom && psql -d backroom -f backend/migrations/init.sql \
 *     -f backend/migrations/002_mpesa_payments.sql
 *   npx ts-node test/entitlement.db-check.ts
 */
import { NestFactory } from '@nestjs/core';
import { AppModule } from '../src/app.module';
import { AuthService } from '../src/modules/auth/auth.service';
import { SubscriptionsService } from '../src/modules/subscriptions/subscriptions.service';
import { DataSource } from 'typeorm';
import { SubscriptionTier } from '../src/database/entities/user.entity';

(async () => {
  const app = await NestFactory.createApplicationContext(AppModule, { logger: false });
  console.log('1. container resolved — no DI cycle, all providers constructed');

  const ds = app.get(DataSource);
  const auth = app.get(AuthService);
  const subs = app.get(SubscriptionsService);

  await ds.query('TRUNCATE subscriptions, payments, users CASCADE');

  // A user whose cached column still says plus but whose period ended yesterday.
  const uid = (await ds.query(
    `INSERT INTO users (subscription_tier, device_id) VALUES ('plus','dev-lapsed') RETURNING id`))[0].id;
  await ds.query(
    `INSERT INTO subscriptions (user_id,provider,provider_subscription_id,tier,started_at,expires_at,is_active)
     VALUES ($1,'mpesa','ws_lapsed','plus',NOW()-interval '40 days',NOW()-interval '1 day',true)`, [uid]);

  const cached = (await ds.query(`SELECT subscription_tier FROM users WHERE id=$1`, [uid]))[0].subscription_tier;
  console.log(`2. cached users.subscription_tier = ${cached}  (stale on purpose)`);
  console.log(`3. derived currentTier            = ${await subs.currentTier(uid)}`);

  const res = await auth.authenticateAnonymous({ deviceId: 'dev-lapsed' } as any);
  const claim = JSON.parse(Buffer.from(res.accessToken.split('.')[1], 'base64').toString()).subscriptionTier;
  console.log(`4. JWT claim in the minted token  = ${claim}`);
  console.log(`5. authResponse.user tier         = ${res.user.subscriptionTier}`);

  const refreshed = await auth.refreshToken(uid);
  const rclaim = JSON.parse(Buffer.from(refreshed!.accessToken.split('.')[1], 'base64').toString()).subscriptionTier;
  console.log(`6. JWT claim after refresh        = ${rclaim}`);

  const ok = claim === SubscriptionTier.FREE && rclaim === SubscriptionTier.FREE
    && res.user.subscriptionTier === SubscriptionTier.FREE && cached === 'plus';
  console.log(ok ? '\nPASS: the lapsed user gets a free token despite the stale column'
                 : '\nFAIL: a stale column still leaks into the token');
  await app.close();
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('BOOT FAILED:', e.message); process.exit(1); });
