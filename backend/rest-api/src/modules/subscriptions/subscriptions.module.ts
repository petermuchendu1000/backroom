import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Subscription } from '../../database/entities/subscription.entity';
import { User } from '../../database/entities/user.entity';
import { MpesaModule } from '../mpesa/mpesa.module';
import { SubscriptionsService } from './subscriptions.service';
import { SubscriptionsSweep } from './subscriptions.sweep';

// Subscriptions module - manages subscription state
// Integrates with M-Pesa for payments
@Module({
  imports: [MpesaModule, TypeOrmModule.forFeature([Subscription, User])],
  controllers: [],
  providers: [SubscriptionsService, SubscriptionsSweep],
  exports: [SubscriptionsService],
})
export class SubscriptionsModule {}
