import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { SubscriptionTier } from './user.entity';

/**
 * Providers the `subscription_provider` enum accepts.
 *
 * MPESA was added by backend/migrations/002_mpesa_payments.sql. Before that the enum
 * held only stripe/google_play/apple while subscriptions.provider is NOT NULL, so an
 * M-Pesa subscription could not be written at all.
 */
export enum SubscriptionProvider {
  STRIPE = 'stripe',
  GOOGLE_PLAY = 'google_play',
  APPLE = 'apple',
  /** Safaricom Daraja, spoken to directly. Added by 002_mpesa_payments.sql. */
  MPESA = 'mpesa',
  /** The PalPluss aggregator. Added by 003_payment_gateways.sql. */
  PALPLUSS = 'palpluss',
}

@Entity('subscriptions')
export class Subscription {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id' })
  @Index()
  userId: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;

  @Column({ type: 'enum', enum: SubscriptionProvider })
  provider: SubscriptionProvider;

  /** For M-Pesa this is the CheckoutRequestID: the payment that bought this period. */
  @Column({ name: 'provider_subscription_id' })
  providerSubscriptionId: string;

  // Explicit type, as above.
  @Column({ name: 'provider_customer_id', type: 'text', nullable: true })
  providerCustomerId: string | null;

  @Column({ type: 'enum', enum: SubscriptionTier })
  tier: SubscriptionTier;

  @Column({ name: 'started_at', type: 'timestamptz' })
  startedAt: Date;

  @Column({ name: 'expires_at', type: 'timestamptz', nullable: true })
  expiresAt: Date | null;

  @Column({ name: 'cancelled_at', type: 'timestamptz', nullable: true })
  cancelledAt: Date | null;

  @Column({ name: 'is_active', default: true })
  isActive: boolean;

  @Column({ name: 'provider_data', type: 'jsonb', default: () => "'{}'::jsonb" })
  providerData: Record<string, unknown>;
}
