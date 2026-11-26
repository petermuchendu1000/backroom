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
import { PaymentProvider } from './payment-gateway';

/** What applying a result did. Returned so the caller can log precisely. */
export enum CallbackOutcome {
  ACTIVATED = 'activated',
  ALREADY_APPLIED = 'already_applied',
  UNKNOWN_CHECKOUT = 'unknown_checkout',
  PAYMENT_FAILED = 'payment_failed',
  AMOUNT_MISMATCH = 'amount_mismatch',
}

/**
 * A payment outcome, in terms this service understands, whichever gateway reported it.
 *
 * Every field is already normalised by the gateway that produced it: no Daraja
 * CallbackMetadata item arrays, no PalPluss event_type strings. That translation belongs
 * next to the vendor, so that what happens to a payment is written once.
 */
export interface GatewayCallback {
  /** Which gateway is speaking. Scopes the lookup so one cannot resolve another's row. */
  provider: PaymentProvider;
  /** The gateway's correlation id, matched against payments.checkout_request_id. */
  gatewayRef: string;
  success: boolean;
  /** Whole shillings the gateway says were paid, or null when it did not say. */
  amountKes: number | null;
  /** M-Pesa confirmation number. Present on success, null otherwise. */
  receipt: string | null;
  /** The gateway's own code, kept verbatim for reconciliation. */
  resultCode: number | null;
  resultDesc: string;
}

/** Which subscription provider a gateway's payments are recorded under. */
const SUBSCRIPTION_PROVIDER: Record<PaymentProvider, SubscriptionProvider> = {
  [PaymentProvider.PALPLUSS]: SubscriptionProvider.PALPLUSS,
  [PaymentProvider.DARAJA]: SubscriptionProvider.MPESA,
};

/**
 * Owns what happens to a payment, as opposed to how any one gateway is spoken to.
 *
 * The result endpoint has to be public. PalPluss signs its callbacks and Daraja signs
 * nothing, so this layer assumes the weaker of the two and trusts no callback's own
 * account of itself: a result is applied only when it names a correlation id WE issued
 * through THAT gateway, that attempt is still pending, and the amount paid is the amount
 * we asked for. Anything else is recorded and ignored. Signature verification, where
 * available, sits in front of this and removes forgeries earlier — it does not replace
 * these checks.
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
   * Record the attempt. Called with the correlation id the gateway returned, before the
   * person has had time to enter a PIN.
   */
  async recordPending(args: {
    userId: string;
    provider: PaymentProvider;
    checkoutRequestId: string;
    merchantRequestId: string | null;
    reference: string;
    plan: SubscriptionPlan;
    amountKes: number;
    phoneNumber: string;
  }): Promise<Payment> {
    const payment = this.payments.create({
      userId: args.userId,
      provider: args.provider,
      checkoutRequestId: args.checkoutRequestId,
      merchantRequestId: args.merchantRequestId,
      reference: args.reference,
      plan: args.plan,
      amountKes: args.amountKes,
      phoneNumber: args.phoneNumber,
      status: PaymentStatus.PENDING,
    });
    return this.payments.save(payment);
  }

  async applyCallback(
    result: GatewayCallback,
    raw?: Record<string, unknown>,
  ): Promise<CallbackOutcome> {
    const payment = await this.payments.findOne({
      where: { provider: result.provider, checkoutRequestId: result.gatewayRef },
    });

    // A result naming an id we never issued buys nothing. With an unsigned gateway this
    // is the main thing standing between the open endpoint and a free subscription.
    if (!payment) {
      this.logger.warn(
        `Callback from ${result.provider} for unknown ref ${result.gatewayRef} — ignored`,
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

    // A success with no amount is not evidence that the right amount was paid.
    const paid = Number(result.amountKes);
    if (!Number.isFinite(paid) || Math.round(paid) !== payment.amountKes) {
      this.logger.error(
        `Callback amount ${result.amountKes} does not match the ` +
          `${payment.amountKes} requested for ${result.gatewayRef} — not activating`,
      );
      await this.finish(payment.id, PaymentStatus.FAILED, result, raw, null);
      return CallbackOutcome.AMOUNT_MISMATCH;
    }

    const receipt = result.receipt ?? null;

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
          provider: SUBSCRIPTION_PROVIDER[result.provider],
          providerSubscriptionId: payment.checkoutRequestId,
          tier: SubscriptionTier.PLUS,
          startedAt,
          expiresAt,
          isActive: true,
          providerData: { receipt, plan: payment.plan, reference: payment.reference },
        }),
      );

      await manager
        .getRepository(User)
        .update({ id: payment.userId }, { subscriptionTier: SubscriptionTier.PLUS });

      this.logger.log(
        `Subscription activated for payment ${payment.id} (${payment.plan}) via ` +
          `${result.provider}, expires ${expiresAt.toISOString()}`,
      );
      return CallbackOutcome.ACTIVATED;
    });
  }

  /** Terminal state for an attempt that will not grant anything. */
  private async finish(
    id: string,
    status: PaymentStatus,
    result: GatewayCallback,
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
