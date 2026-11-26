/**
 * One-off repair: make users.subscription_tier agree with the subscriptions table.
 *
 * Until the lapse sweep existed, activation set users.subscription_tier = 'plus' and
 * nothing ever moved it back, so anyone whose period has since ended is still carrying
 * paid access. This corrects the rows that are already wrong.
 *
 * DRY RUN BY DEFAULT. It prints what it would change and exits without writing.
 * Pass --apply to actually write.
 *
 *   npm run backfill:tiers              # report only
 *   npm run backfill:tiers -- --apply   # write
 *
 * What it will and will not touch:
 *
 *   DOWNGRADE  a user above 'free' who HAS subscription rows and none of them is live.
 *              Their paid period demonstrably ended, so this is the lapse being repaired.
 *
 *   UPGRADE    a user whose live subscription outranks their cached tier. This is the
 *              mirror failure: the subscription row was written but the user update was
 *              not, so somebody paid and never got access. Worth repairing in the same
 *              pass, and it only ever grants what a live subscription already says.
 *
 *   LEAVE      a user above 'free' with NO subscription rows at all. A lapse cannot
 *              explain that — a trial (SUBSCRIPTION_TRIAL_DAYS is configured, though
 *              nothing grants one yet), a manual grant or an import can. Downgrading
 *              them would revoke access this script cannot prove was unearned, so they
 *              are counted and listed for a human instead. Stopping beats guessing.
 */
import 'reflect-metadata';
import { DataSource } from 'typeorm';
import { Payment } from '../src/database/entities/payment.entity';
import { Subscription } from '../src/database/entities/subscription.entity';
import { SubscriptionTier, User } from '../src/database/entities/user.entity';

const APPLY = process.argv.includes('--apply');

const LIVE = `
  s.is_active = true
  AND s.cancelled_at IS NULL
  AND (s.expires_at IS NULL OR s.expires_at > $1)
`;

const dataSource = new DataSource({
  type: 'postgres',
  host: process.env.PGHOST ?? 'localhost',
  port: Number(process.env.PGPORT ?? 5432),
  username: process.env.PGUSER ?? 'backroom',
  password: process.env.PGPASSWORD ?? 'backroom_dev_password',
  database: process.env.PGDATABASE ?? 'backroom',
  entities: [Payment, Subscription, User],
  synchronize: false,
  logging: false,
});

async function main(): Promise<void> {
  await dataSource.initialize();
  const now = new Date();

  // Not every count needs the timestamp; passing one to a query without a placeholder
  // is a bind error, so params are explicit.
  const count = async (sql: string, params: unknown[] = [now]): Promise<number> =>
    Number((await dataSource.query(sql, params))[0].n);

  const toDowngrade = await count(`
    SELECT count(*)::int AS n FROM users u
     WHERE u.subscription_tier <> 'free'
       AND EXISTS (SELECT 1 FROM subscriptions s WHERE s.user_id = u.id)
       AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.user_id = u.id AND ${LIVE})
  `);

  const toUpgrade = await count(`
    SELECT count(*)::int AS n
      FROM users u
      JOIN LATERAL (
        SELECT s.tier FROM subscriptions s
         WHERE s.user_id = u.id AND ${LIVE}
         ORDER BY s.expires_at DESC NULLS FIRST
         LIMIT 1
      ) live ON TRUE
     WHERE u.subscription_tier <> live.tier
  `);

  const unexplained = await count(
    `SELECT count(*)::int AS n FROM users u
      WHERE u.subscription_tier <> 'free'
        AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.user_id = u.id)`,
    [],
  );

  console.log(`${APPLY ? 'APPLYING' : 'DRY RUN (pass --apply to write)'}  at ${now.toISOString()}`);
  console.log(`  lapsed, will be downgraded to free : ${toDowngrade}`);
  console.log(`  paid but never granted, will upgrade: ${toUpgrade}`);
  console.log(`  above free with no subscription row : ${unexplained}  (LEFT ALONE — needs a human)`);

  if (unexplained > 0) {
    const rows = await dataSource.query(
      `SELECT u.id, u.subscription_tier FROM users u
        WHERE u.subscription_tier <> 'free'
          AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.user_id = u.id)
        ORDER BY u.created_at LIMIT 50`,
    );
    console.log('  first of those (up to 50):');
    for (const row of rows) console.log(`    ${row.id}  ${row.subscription_tier}`);
  }

  if (!APPLY) {
    await dataSource.destroy();
    return;
  }

  const changed = await dataSource.transaction(async (manager) => {
    const [downgraded]: [Array<{ id: string }>, number] = await manager.query(
      `UPDATE users u
          SET subscription_tier = $2, updated_at = NOW()
        WHERE u.subscription_tier <> 'free'
          AND EXISTS (SELECT 1 FROM subscriptions s WHERE s.user_id = u.id)
          AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.user_id = u.id AND ${LIVE})
      RETURNING u.id`,
      [now, SubscriptionTier.FREE],
    );

    const [upgraded]: [Array<{ id: string }>, number] = await manager.query(
      `UPDATE users u
          SET subscription_tier = live.tier, updated_at = NOW()
         FROM (
           SELECT DISTINCT ON (s.user_id) s.user_id, s.tier
             FROM subscriptions s
            WHERE ${LIVE}
            ORDER BY s.user_id, s.expires_at DESC NULLS FIRST
         ) live
        WHERE u.id = live.user_id
          AND u.subscription_tier <> live.tier
      RETURNING u.id`,
      [now],
    );

    // Close subscription rows left open past their expiry, so the sweep's own
    // bookkeeping starts from a clean state too.
    const [closed]: [Array<{ id: string }>, number] = await manager.query(
      `UPDATE subscriptions
          SET is_active = false, updated_at = NOW()
        WHERE is_active = true
          AND expires_at IS NOT NULL
          AND expires_at <= $1
      RETURNING id`,
      [now],
    );

    return { downgraded: downgraded.length, upgraded: upgraded.length, closed: closed.length };
  });

  console.log(
    `  DONE: ${changed.downgraded} downgraded, ${changed.upgraded} upgraded, ` +
      `${changed.closed} subscription row(s) closed`,
  );
  await dataSource.destroy();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
