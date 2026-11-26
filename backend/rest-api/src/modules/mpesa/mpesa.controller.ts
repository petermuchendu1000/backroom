import {
  Controller,
  Post,
  Body,
  HttpCode,
  HttpStatus,
  Logger,
  UseGuards,
  Req,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';
import { ConfigService } from '@nestjs/config';
import { MpesaService, MpesaCallbackData } from './mpesa.service';
import { CallbackOutcome, PaymentsService } from './payments.service';
import { SubscriptionPlan } from '../../database/entities/payment.entity';
import { InitiatePaymentDto, QueryPaymentDto } from './dto/mpesa.dto';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';

@ApiTags('mpesa')
@Controller('mpesa')
export class MpesaController {
  private readonly logger = new Logger(MpesaController.name);

  constructor(
    private readonly mpesaService: MpesaService,
    private readonly paymentsService: PaymentsService,
    private readonly configService: ConfigService,
  ) {}

  /**
   * Initiate M-Pesa STK Push payment for subscription
   */
  @Post('pay')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Initiate M-Pesa payment for subscription' })
  @ApiResponse({ status: 200, description: 'STK Push initiated successfully' })
  @ApiResponse({ status: 400, description: 'Invalid phone number or payment failed' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async initiatePayment(@Body() dto: InitiatePaymentDto, @Req() req: any) {
    const userId = req.user?.id;

    // Get price based on plan
    const monthlyPrice = this.configService.get<number>('subscription.monthlyPrice') ?? 500;
    const yearlyPrice = this.configService.get<number>('subscription.yearlyPrice') ?? 5000;
    const amount = dto.plan === 'yearly' ? yearlyPrice : monthlyPrice;

    const response = await this.mpesaService.initiateSTKPush({
      phoneNumber: dto.phoneNumber,
      amount,
      accountReference: `BKRM${userId?.slice(0, 6) || 'SUB'}`,
      transactionDesc: `Ongero ${dto.plan}`,
    });

    // Record the attempt before the prompt is answered. The callback carries nothing
    // that identifies the payer except this CheckoutRequestID, so without this row it
    // could not be attributed to anyone.
    await this.paymentsService.recordPending({
      userId,
      checkoutRequestId: response.CheckoutRequestID,
      merchantRequestId: response.MerchantRequestID ?? null,
      plan: dto.plan as SubscriptionPlan,
      amountKes: amount,
      phoneNumber: dto.phoneNumber,
    });

    return {
      success: true,
      message: 'Please check your phone and enter M-Pesa PIN',
      checkoutRequestId: response.CheckoutRequestID,
      merchantRequestId: response.MerchantRequestID,
      plan: dto.plan,
      amount,
    };
  }

  /**
   * Query payment status
   */
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
   * M-Pesa callback endpoint (called by Safaricom)
   * This must be publicly accessible (no auth)
   */
  @Post('callback')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'M-Pesa callback webhook (Safaricom)' })
  @ApiResponse({ status: 200, description: 'Callback processed' })
  async handleCallback(@Body() data: MpesaCallbackData) {
    const result = this.mpesaService.processCallback(data);
    const outcome = await this.paymentsService.applyCallback(
      result,
      data as unknown as Record<string, unknown>,
    );

    switch (outcome) {
      case CallbackOutcome.ACTIVATED:
        this.logger.log(`Subscription activated for ${result.checkoutRequestId}`);
        break;
      case CallbackOutcome.ALREADY_APPLIED:
        this.logger.log(`Duplicate callback for ${result.checkoutRequestId} — ignored`);
        break;
      case CallbackOutcome.UNKNOWN_CHECKOUT:
        // Either a stale retry or someone POSTing at a public endpoint. Neither grants
        // anything, and we still answer 200 so Safaricom stops retrying.
        this.logger.warn(`Callback for unknown ${result.checkoutRequestId} — ignored`);
        break;
      case CallbackOutcome.AMOUNT_MISMATCH:
        this.logger.error(
          `Amount mismatch for ${result.checkoutRequestId} — not activated`,
        );
        break;
      case CallbackOutcome.PAYMENT_FAILED:
        this.logger.warn(
          `Payment failed for ${result.checkoutRequestId}: ${result.resultDesc}`,
        );
        break;
    }

    // Safaricom expects a specific response format
    return {
      ResultCode: 0,
      ResultDesc: 'Callback received successfully',
    };
  }

  /**
   * M-Pesa timeout endpoint
   */
  @Post('timeout')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'M-Pesa timeout webhook' })
  async handleTimeout(@Body() data: any) {
    this.logger.warn('M-Pesa request timed out', data);

    return {
      ResultCode: 0,
      ResultDesc: 'Timeout handled',
    };
  }
}

