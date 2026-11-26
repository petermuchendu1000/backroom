import { HttpModule } from '@nestjs/axios';
import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Payment } from '../../database/entities/payment.entity';
import { Subscription } from '../../database/entities/subscription.entity';
import { User } from '../../database/entities/user.entity';
import { MpesaModule } from '../mpesa/mpesa.module';
import { DarajaGateway } from './daraja.gateway';
import { MpesaController } from './mpesa.controller';
import { PalplussWebhookController } from './palpluss/palpluss-webhook.controller';
import { PalplussGateway } from './palpluss/palpluss.gateway';
import { PaymentGatewayRegistry } from './payment-gateways.registry';
import { PaymentsController } from './payments.controller';
import { PaymentsService } from './payments.service';

/**
 * Everything to do with money arriving.
 *
 * The dependency runs one way: this module imports MpesaModule for the Daraja HTTP
 * client, and MpesaModule imports nothing of ours. That is why the Daraja controller
 * lives here rather than next to the client it calls — it needs PaymentsService, and
 * putting it in MpesaModule would close the loop into a cycle Nest refuses to resolve.
 */
@Module({
  imports: [
    ConfigModule,
    MpesaModule,
    TypeOrmModule.forFeature([Payment, Subscription, User]),
    HttpModule.register({
      // PalPluss sends the STK prompt synchronously and only answers once Safaricom
      // has accepted it, so this is not a local call.
      timeout: 30000,
      maxRedirects: 0,
    }),
  ],
  controllers: [PaymentsController, PalplussWebhookController, MpesaController],
  providers: [PaymentsService, PalplussGateway, DarajaGateway, PaymentGatewayRegistry],
  exports: [PaymentsService, PaymentGatewayRegistry],
})
export class PaymentsModule {}
