import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Logger,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiBearerAuth, ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { SubscriptionPlan } from '../../database/entities/payment.entity';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { MpesaCallbackData, MpesaService } from '../mpesa/mpesa.service';
import { DarajaGateway } from './daraja.gateway';
import { InitiatePaymentDto, QueryPaymentDto } from './dto/payment.dto';
import {
  PROMPT_DESCRIPTION,
  PaymentProvider,
  generateReference,
} from './payment-gateway';
import { CallbackOutcome, GatewayCallback, PaymentsService } from './payments.service';

/**
 * The direct-to-Safaricom routes, kept as they were.
 *
 * /mpesa/callback is the URL configured with Safaricom (MPESA_CALLBACK_URL), so it
 * cannot move without changing that registration; /mpesa/pay stays beside it so a
 * deployment that has not switched to PalPluss keeps working unchanged. New clients
 * should use /payments/pay, which routes through whichever gateway is configured.
 *
 * Daraja signs nothing it sends here. That is the reason this endpoint can only defend
 * itself with the correlation and amount checks in PaymentsService — PalPluss's
 * equivalent adds a signature in front of those.
 */
@ApiTags('mpesa')
@Controller('mpesa')
export class MpesaController {
  private readonly logger = new Logger(MpesaController.name);

  constructor(
    private readonly mpesaService: MpesaService,
    private readonly daraja: DarajaGateway,
    private readonly paymentsService: PaymentsService,
    private readonly configService: ConfigService,
  ) {}

  @Post('pay')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Initiate an M-Pesa payment directly through Daraja' })
  @ApiResponse({ status: 201, description: 'STK Push initiated successfully' })
  @ApiResponse({ status: 400, description: 'Invalid phone number or payment failed' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async initiatePayment(@Body() dto: InitiatePaymentDto, @Req() req: { user?: { id?: string } }) {
    const userId = req.user?.id as string;

    const monthlyPrice = this.configService.get<number>('subscription.monthlyPrice') ?? 500;
    const yearlyPrice = this.configService.get<number>('subscription.yearlyPrice') ?? 5000;
    const amount = dto.plan === 'yearly' ? yearlyPrice : monthlyPrice;

    const reference = generateReference();
    const started = await this.daraja.initiate({
      amountKes: amount,
      phoneNumber: dto.phoneNumber,
      reference,
      description: PROMPT_DESCRIPTION,
    });

    // Record the attempt before the prompt is answered. The callback carries nothing
    // that identifies the payer except this CheckoutRequestID, so without this row it
    // could not be attributed to anyone.
    await this.paymentsService.recordPending({
      userId,
      provider: PaymentProvider.DARAJA,
      checkoutRequestId: started.gatewayRef,
      merchantRequestId: started.providerRef,
      reference,
      plan: dto.plan as SubscriptionPlan,
      amountKes: amount,
      phoneNumber: dto.phoneNumber,
    });

    return {
      success: true,
      message: started.customerMessage,
      checkoutRequestId: started.gatewayRef,
      merchantRequestId: started.providerRef,
      reference,
      plan: dto.plan,
      amount,
    };
  }

  @Post('query')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Check M-Pesa payment status' })
  @ApiResponse({ status: 200, description: 'Payment status retrieved' })
  async queryPaymentStatus(@Body() dto: QueryPaymentDto) {
    const status = await this.mpesaService.querySTKPushStatus(dto.checkoutRequestId);

    return {
      success: status.ResultCode === '0',
      resultCode: status.ResultCode,
      resultDesc: status.ResultDesc,
    };
  }

  /**
   * Called by Safaricom. Must be publicly accessible, and is never authenticated.
   */
  @Post('callback')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'M-Pesa callback webhook (Safaricom)' })
  @ApiResponse({ status: 200, description: 'Callback processed' })
  async handleCallback(@Body() data: MpesaCallbackData) {
    const result = this.mpesaService.processCallback(data);
    const callback = darajaCallback(result);
    const outcome = await this.paymentsService.applyCallback(
      callback,
      data as unknown as Record<string, unknown>,
    );

    switch (outcome) {
      case CallbackOutcome.ACTIVATED:
        this.logger.log(`Subscription activated for ${callback.gatewayRef}`);
        break;
      case CallbackOutcome.ALREADY_APPLIED:
        this.logger.log(`Duplicate callback for ${callback.gatewayRef} — ignored`);
        break;
      case CallbackOutcome.UNKNOWN_CHECKOUT:
        // Either a stale retry or someone POSTing at a public endpoint. Neither grants
        // anything, and we still answer 200 so Safaricom stops retrying.
        this.logger.warn(`Callback for unknown ${callback.gatewayRef} — ignored`);
        break;
      case CallbackOutcome.AMOUNT_MISMATCH:
        this.logger.error(`Amount mismatch for ${callback.gatewayRef} — not activated`);
        break;
      case CallbackOutcome.PAYMENT_FAILED:
        this.logger.warn(
          `Payment failed for ${callback.gatewayRef}: ${callback.resultDesc}`,
        );
        break;
    }

    // Safaricom expects a specific response format
    return {
      ResultCode: 0,
      ResultDesc: 'Callback received successfully',
    };
  }

  @Post('timeout')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'M-Pesa timeout webhook' })
  async handleTimeout(@Body() data: unknown) {
    this.logger.warn('M-Pesa request timed out', data);

    return {
      ResultCode: 0,
      ResultDesc: 'Timeout handled',
    };
  }
}

/**
 * Daraja's callback shape, flattened into the one PaymentsService understands.
 *
 * The amount and the receipt arrive inside CallbackMetadata as a list of {Name, Value}
 * pairs that MpesaService has already turned into an object; pulling them out here is
 * what keeps that vendor detail out of the service that decides what a payment buys.
 */
export function darajaCallback(result: {
  success: boolean;
  checkoutRequestId: string;
  resultCode: number;
  resultDesc: string;
  metadata?: Record<string, unknown>;
}): GatewayCallback {
  const amount = Number(result.metadata?.Amount);
  const receipt = result.metadata?.MpesaReceiptNumber;

  return {
    provider: PaymentProvider.DARAJA,
    gatewayRef: result.checkoutRequestId,
    success: result.success,
    amountKes: Number.isFinite(amount) ? Math.round(amount) : null,
    receipt: receipt !== undefined && receipt !== null ? String(receipt) : null,
    resultCode: Number.isInteger(result.resultCode) ? result.resultCode : null,
    resultDesc: result.resultDesc,
  };
}
