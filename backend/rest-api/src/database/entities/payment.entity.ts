import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

export enum PaymentStatus {
  PENDING = 'pending',
  SUCCEEDED = 'succeeded',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
}

export enum SubscriptionPlan {
  MONTHLY = 'monthly',
  YEARLY = 'yearly',
}

/**
 * One row per STK Push attempt.
 *
 * Written before the prompt reaches the phone, because the gateway's result carries
 * nothing that identifies the payer to us except the correlation id it echoes back.
 * Without a row to match it against, a result cannot be attributed, cannot be made
 * idempotent, and cannot be distinguished from a forged POST to a necessarily public
 * endpoint.
 */
@Entity('payments')
export class Payment {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id' })
  @Index()
  userId: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;

  /**
   * Which gateway issued checkoutRequestId. Deliberately a plain string column rather
   * than an enum, so adding a gateway is a deploy and not a migration; the allowed
   * values are the PaymentProvider enum. A webhook is matched on (provider, ref), never
   * on the ref alone, so one gateway cannot resolve another's payment.
   */
  @Column({ type: 'text', default: 'daraja' })
  provider: string;

  /**
   * The gateway's id for this attempt, and the idempotency key for its result.
   * Unique — a replayed result cannot create a second payment, and one naming an id we
   * never issued matches nothing.
   */
  @Column({ name: 'checkout_request_id', unique: true })
  checkoutRequestId: string;

  /**
   * The short reference WE generated and sent. PalPluss echoes it back as
   * external_reference and caps it at 12 characters. Nullable: rows written before
   * migration 003 never had one.
   */
  @Column({ type: 'text', nullable: true })
  reference: string | null;

  // Explicit type: TypeORM reflects `string | null` as Object and cannot map it.
  @Column({ name: 'merchant_request_id', type: 'text', nullable: true })
  merchantRequestId: string | null;

  @Column({ type: 'text' })
  plan: SubscriptionPlan;

  /** What we asked for, in whole shillings. The callback is checked against this. */
  @Column({ name: 'amount_kes', type: 'int' })
  amountKes: number;

  @Column({ name: 'phone_number' })
  phoneNumber: string;

  @Column({ type: 'enum', enum: PaymentStatus, default: PaymentStatus.PENDING })
  status: PaymentStatus;

  @Column({ name: 'result_code', type: 'int', nullable: true })
  resultCode: number | null;

  @Column({ name: 'result_desc', type: 'text', nullable: true })
  resultDesc: string | null;

  @Column({ name: 'mpesa_receipt_number', type: 'text', nullable: true })
  mpesaReceiptNumber: string | null;

  @Column({ name: 'completed_at', type: 'timestamptz', nullable: true })
  completedAt: Date | null;

  @Column({ name: 'raw_callback', type: 'jsonb', nullable: true })
  rawCallback: Record<string, unknown> | null;
}
