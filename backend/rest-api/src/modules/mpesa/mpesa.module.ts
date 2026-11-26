import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { MpesaService } from './mpesa.service';

/**
 * The Safaricom Daraja HTTP client, and nothing else.
 *
 * Everything that decides what a payment MEANS moved to PaymentsModule, which imports
 * this one. Keeping this module free of the database and of PaymentsService is what
 * keeps that dependency acyclic — and it leaves one file whose whole job is knowing how
 * Daraja talks.
 */
@Module({
  imports: [
    ConfigModule,
    HttpModule.register({
      timeout: 30000,
      maxRedirects: 5,
    }),
  ],
  providers: [MpesaService],
  exports: [MpesaService],
})
export class MpesaModule {}
