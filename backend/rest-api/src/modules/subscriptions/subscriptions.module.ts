import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Subscription } from '../../database/entities/subscription.entity';
import { User } from '../../database/entities/user.entity';
import { SubscriptionsController } from './subscriptions.controller';
import { SubscriptionsService } from './subscriptions.service';
import { SubscriptionsSweep } from './subscriptions.sweep';

// Owns subscription state. It no longer imports MpesaModule: nothing here used it, and
// AuthModule now depends on this module, so an unnecessary edge risks a cycle later.
@Module({
  imports: [TypeOrmModule.forFeature([Subscription, User])],
  controllers: [SubscriptionsController],
  providers: [SubscriptionsService, SubscriptionsSweep],
  exports: [SubscriptionsService],
})
export class SubscriptionsModule {}
