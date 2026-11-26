import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Payment } from '../../database/entities/payment.entity';
import { Subscription } from '../../database/entities/subscription.entity';
import { User } from '../../database/entities/user.entity';
import { MpesaService } from './mpesa.service';
import { PaymentsService } from './payments.service';
import { MpesaController } from './mpesa.controller';

@Module({
  imports: [
    ConfigModule,
    TypeOrmModule.forFeature([Payment, Subscription, User]),
    HttpModule.register({
      timeout: 30000,
      maxRedirects: 5,
    }),
  ],
  controllers: [MpesaController],
  providers: [MpesaService, PaymentsService],
  exports: [MpesaService, PaymentsService],
})
export class MpesaModule {}

