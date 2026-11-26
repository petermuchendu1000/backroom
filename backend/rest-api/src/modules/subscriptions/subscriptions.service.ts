import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { Subscription } from '../../database/entities/subscription.entity';
import { SubscriptionTier, User } from '../../database/entities/user.entity';

export interface SweepResult {
  /** Subscriptions whose paid period has run out and are now closed. */
  lapsed: number;
  /** Users moved back to free because nothing active was left. */
  downgraded: number;
}

/**
 * Keeps paid access honest once the money stops.
 *
 * Activation writes users.subscription_tier = 'plus', which is a denormalised copy of
 * something the subscriptions table already knows. Nothing used to move it back, so the
 * first person who ever paid stayed Plus forever.
 *
 * Two mechanisms, deliberately:
 *
 *   [currentTier] derives entitlement from the subscriptions table every time it is
 *   asked. It is correct the instant a subscription expires, with no job having run, so
 *   a sweep that is late, wedged or never deployed cannot hand out free access.
 *
 *   [expireLapsed] reconciles the denormalised column so the cheap read on users stays
 *   true, and so the data is not quietly wrong for anything that reads it directly.
 *
 * The derived check is the one that is load-bearing. The sweep is bookkeeping.
 */
@Injectable()
export class SubscriptionsService {
  private readonly logger = new Logger(SubscriptionsService.name);

  constructor(
    @InjectRepository(Subscription)
    private readonly subscriptions: Repository<Subscription>,
    private readonly dataSource: DataSource,
  ) {}

  /**
   * What this user is entitled to right now, from the subscriptions table rather than
   * from the cached column on users.
   */
  async currentTier(userId: string, now: Date = new Date()): Promise<SubscriptionTier> {
    return (await this.liveSubscription(userId, now))?.tier ?? SubscriptionTier.FREE;
  }

  /** Tier plus when it runs out, for anything that shows the person their status. */
  async current(
    userId: string,
    now: Date = new Date(),
  ): Promise<{ tier: SubscriptionTier; expiresAt: Date | null }> {
    const live = await this.liveSubscription(userId, now);
    return {
      tier: live?.tier ?? SubscriptionTier.FREE,
      expiresAt: live?.expiresAt ?? null,
    };
  }

  /** The one definition of "currently entitled", used by everything above. */
  private async liveSubscription(userId: string, now: Date): Promise<Subscription | null> {
    return this.subscriptions
      .createQueryBuilder('s')
      .where('s.user_id = :userId', { userId })
      .andWhere('s.is_active = true')
      .andWhere('s.cancelled_at IS NULL')
      .andWhere('(s.expires_at IS NULL OR s.expires_at > :now)', { now })
      .orderBy('s.expires_at', 'DESC', 'NULLS FIRST')
      .getOne();
  }

  async isPlus(userId: string, now: Date = new Date()): Promise<boolean> {
    return (await this.currentTier(userId, now)) !== SubscriptionTier.FREE;
  }

  /**
   * Close every subscription whose period has run out, then move back to free any user
   * left with nothing active.
   *
   * Both statements are set-based and conditional, so running this twice, or running it
   * on two instances at once, changes nothing the second time.
   */
  async expireLapsed(now: Date = new Date()): Promise<SweepResult> {
    return this.dataSource.transaction(async (manager) => {
      // TypeORM returns [rows, affectedCount] for an UPDATE ... RETURNING, so the
      // array itself always has length 2. Destructure, or every count is the number 2.
      const [lapsed]: [Array<{ user_id: string }>, number] = await manager.query(
        `UPDATE subscriptions
            SET is_active = false, updated_at = NOW()
          WHERE is_active = true
            AND expires_at IS NOT NULL
            AND expires_at <= $1
        RETURNING user_id`,
        [now],
      );

      if (lapsed.length === 0) {
        return { lapsed: 0, downgraded: 0 };
      }

      const userIds = [...new Set(lapsed.map((row) => row.user_id))];

      // Only drop the tier for people with nothing else live. Someone who renewed
      // early, or holds a second subscription, keeps their access.
      const [downgradedRows]: [Array<{ id: string }>, number] = await manager.query(
        `UPDATE users u
            SET subscription_tier = $2, updated_at = NOW()
          WHERE u.id = ANY($1::uuid[])
            AND u.subscription_tier <> $2
            AND NOT EXISTS (
                  SELECT 1 FROM subscriptions s
                   WHERE s.user_id = u.id
                     AND s.is_active = true
                     AND s.cancelled_at IS NULL
                     AND (s.expires_at IS NULL OR s.expires_at > $3)
                )
        RETURNING u.id`,
        [userIds, SubscriptionTier.FREE, now],
      );

      const result: SweepResult = {
        lapsed: lapsed.length,
        downgraded: downgradedRows.length,
      };

      this.logger.log(
        `Expired ${result.lapsed} subscription(s); moved ${result.downgraded} user(s) back to free`,
      );
      return result;
    });
  }
}
