import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import {
  Payment,
  PaymentStatus,
  SubscriptionPlan,
} from '../../database/entities/payment.entity';
import {
  Subscription,
  SubscriptionProvider,
} from '../../database/entities/subscription.entity';
import { SubscriptionTier, User } from '../../database/entities/user.entity';

/** What applying a callback did. Returned so the caller can log precisely. */
export enum CallbackOutcome {
  ACTIVATED = 'activated',
  ALREADY_APPLIED = 'already_applied',
  UNKNOWN_CHECKOUT = 'unknown_checkout',
  PAYMENT_FAILED = 'payment_failed',
  AMOUNT_MISMATCH = 'amount_mismatch',
}

export interface CallbackResult {
  success: boolean;
  checkoutRequestId: string;
  merchantRequestId: string;
  resultCode: number;
  resultDesc: string;
  metadata?: Record<string, any>;
}

/**
 * Owns what happens to a payment, as opposed to how M-Pesa is spoken to.
 *
 * The callback endpoint has to be public and Safaricom signs nothing it sends, so
 * nothing here trusts the callback's own account of itself. A callback is applied only
 * when it names a CheckoutRequestID we issued, that attempt is still pending, and the
 * amount paid is the amount we asked for. Anything else is recorded and ignored.
 */
@Injectable()
export class PaymentsService {
  private readonly logger = new Logger(PaymentsService.name);

  constructor(
    @InjectRepository(Payment)
    private readonly payments: Repository<Payment>,
    private readonly dataSource: DataSource,
  ) {}

  /**
   * Record the attempt. Called with the CheckoutRequestID the STK Push returned, before
   * the person has had time to enter a PIN.
   */
  async recordPending(args: {
    userId: string;
    checkoutRequestId: string;
    merchantRequestId: string | null;
    plan: SubscriptionPlan;
    amountKes: number;
    phoneNumber: string;
  }): Promise<Payment> {
    const payment = this.payments.create({
      userId: args.userId,
      checkoutRequestId: args.checkoutRequestId,
      merchantRequestId: args.merchantRequestId,
      plan: args.plan,
      amountKes: args.amountKes,
      phoneNumber: args.phoneNumber,
      status: PaymentStatus.PENDING,
    });
    return this.payments.save(payment);
  }

  async applyCallback(
    result: CallbackResult,
    raw?: Record<string, unknown>,
  ): Promise<CallbackOutcome> {
    const payment = await this.payments.findOne({
      where: { checkoutRequestId: result.checkoutRequestId },
    });

    // A callback naming an id we never issued buys nothing. This is the main thing
    // standing between the open endpoint and a free subscription.
    if (!payment) {
      this.logger.warn(
        `Callback for unknown CheckoutRequestID ${result.checkoutRequestId} — ignored`,
      );
      return CallbackOutcome.UNKNOWN_CHECKOUT;
    }

    if (payment.status !== PaymentStatus.PENDING) {
      return CallbackOutcome.ALREADY_APPLIED;
    }

    if (!result.success) {
      await this.finish(payment.id, PaymentStatus.FAILED, result, raw, null);
      return CallbackOutcome.PAYMENT_FAILED;
    }

    const paid = Number(result.metadata?.Amount);
    if (!Number.isFinite(paid) || Math.round(paid) !== payment.amountKes) {
      this.logger.error(
        `Callback amount ${result.metadata?.Amount} does not match the ` +
          `${payment.amountKes} requested for ${result.checkoutRequestId} — not activating`,
      );
      await this.finish(payment.id, PaymentStatus.FAILED, result, raw, null);
      return CallbackOutcome.AMOUNT_MISMATCH;
    }

    const receipt = result.metadata?.MpesaReceiptNumber
      ? String(result.metadata.MpesaReceiptNumber)
      : null;

    return this.dataSource.transaction(async (manager) => {
      // Claim the row atomically. Two callbacks arriving together both read 'pending'
      // above; only the one whose UPDATE matches gets to grant anything.
      const claim = await manager
        .createQueryBuilder()
        .update(Payment)
        .set({
          status: PaymentStatus.SUCCEEDED,
          resultCode: result.resultCode,
          resultDesc: result.resultDesc,
          mpesaReceiptNumber: receipt,
          completedAt: new Date(),
          // eslint-disable-next-line @typescript-eslint/no-explicit-any -- TypeORM's
          // QueryDeepPartialEntity does not accept a plain object for a jsonb column.
          rawCallback: (raw ?? null) as any,
        })
        .where('id = :id AND status = :pending', {
          id: payment.id,
          pending: PaymentStatus.PENDING,
        })
        .execute();

      if (!claim.affected) {
        return CallbackOutcome.ALREADY_APPLIED;
      }

      const startedAt = new Date();
      const expiresAt = expiryFor(payment.plan, startedAt);

      await manager.getRepository(Subscription).save(
        manager.getRepository(Subscription).create({
          userId: payment.userId,
          provider: SubscriptionProvider.MPESA,
          providerSubscriptionId: payment.checkoutRequestId,
          tier: SubscriptionTier.PLUS,
          startedAt,
          expiresAt,
          isActive: true,
          providerData: { receipt, plan: payment.plan },
        }),
      );

      await manager
        .getRepository(User)
        .update({ id: payment.userId }, { subscriptionTier: SubscriptionTier.PLUS });

      this.logger.log(
        `Subscription activated for payment ${payment.id} (${payment.plan}), ` +
          `expires ${expiresAt.toISOString()}`,
      );
      return CallbackOutcome.ACTIVATED;
    });
  }

  /** Terminal state for an attempt that will not grant anything. */
  private async finish(
    id: string,
    status: PaymentStatus,
    result: CallbackResult,
    raw: Record<string, unknown> | undefined,
    receipt: string | null,
  ): Promise<void> {
    await this.payments
      .createQueryBuilder()
      .update(Payment)
      .set({
        status,
        resultCode: result.resultCode,
        resultDesc: result.resultDesc,
        mpesaReceiptNumber: receipt,
        completedAt: new Date(),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- as above
        rawCallback: (raw ?? null) as any,
      })
      .where('id = :id AND status = :pending', { id, pending: PaymentStatus.PENDING })
      .execute();
  }
}

/** Monthly buys a month, yearly buys a year, from the moment the money lands. */
export function expiryFor(plan: SubscriptionPlan, from: Date): Date {
  const expires = new Date(from.getTime());
  if (plan === SubscriptionPlan.YEARLY) {
    expires.setUTCFullYear(expires.getUTCFullYear() + 1);
  } else {
    expires.setUTCMonth(expires.getUTCMonth() + 1);
  }
  return expires;
}
