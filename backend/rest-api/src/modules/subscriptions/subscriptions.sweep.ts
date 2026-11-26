import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { SubscriptionsService } from './subscriptions.service';

/**
 * Runs the lapse sweep on a timer.
 *
 * Hourly rather than nightly because a subscription bought at 14:03 expires at 14:03,
 * and a daily job would leave up to a day of unpaid access on the denormalised column.
 * Entitlement itself does not depend on this running — SubscriptionsService.currentTier
 * is derived — so a missed run costs accuracy in the cached column, not money.
 *
 * Safe to run on every instance: the sweep's statements are conditional and set-based,
 * so concurrent runs converge instead of double-counting.
 */
@Injectable()
export class SubscriptionsSweep {
  private readonly logger = new Logger(SubscriptionsSweep.name);

  constructor(private readonly subscriptions: SubscriptionsService) {}

  @Cron(CronExpression.EVERY_HOUR)
  async sweep(): Promise<void> {
    try {
      const result = await this.subscriptions.expireLapsed();
      if (result.lapsed > 0) {
        this.logger.log(
          `Lapse sweep: ${result.lapsed} expired, ${result.downgraded} downgraded`,
        );
      }
    } catch (error) {
      // A failed sweep must not take the process down: entitlement is still correct
      // because it is derived, and the next run will pick up whatever was missed.
      this.logger.error('Lapse sweep failed', error instanceof Error ? error.stack : error);
    }
  }
}
