import {
  Body,
  Controller,
  Get,
  Logger,
  NotFoundException,
  Param,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { ApiBearerAuth, ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { Repository } from 'typeorm';
import {
  Payment,
  PaymentStatus,
  SubscriptionPlan,
} from '../../database/entities/payment.entity';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { InitiatePaymentDto } from './dto/payment.dto';
import {
  GatewayPaymentState,
  PROMPT_DESCRIPTION,
  PaymentProvider,
  generateReference,
} from './payment-gateway';
import { PaymentGatewayRegistry, asProvider } from './payment-gateways.registry';
import { PaymentsService } from './payments.service';

/**
 * Taking money, gateway-agnostic.
 *
 * The person paying never chooses an aggregator — they see one M-Pesa prompt whichever
 * one we route through — so there is no provider in the request body. Which gateway is
 * used is a deployment decision (PAYMENTS_PROVIDER), recorded on the payment row so the
 * result can be matched back to the gateway that issued it.
 */
@ApiTags('payments')
@Controller('payments')
export class PaymentsController {
  private readonly logger = new Logger(PaymentsController.name);

  constructor(
    private readonly gateways: PaymentGatewayRegistry,
    private readonly payments: PaymentsService,
    private readonly config: ConfigService,
    @InjectRepository(Payment)
    private readonly paymentRepository: Repository<Payment>,
  ) {}

  @Post('pay')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Start a subscription payment (M-Pesa prompt)' })
  @ApiResponse({ status: 201, description: 'Prompt sent; awaiting the customer PIN' })
  @ApiResponse({ status: 400, description: 'Invalid phone number or plan' })
  @ApiResponse({ status: 503, description: 'Payment gateway unavailable' })
  async pay(@Body() dto: InitiatePaymentDto, @Req() req: { user?: { id?: string } }) {
    const userId = req.user?.id as string;
    const amount = this.priceFor(dto.plan);
    const gateway = this.gateways.default();
    const reference = generateReference();

    // The prompt first: if the gateway refuses, there is nothing worth recording, and a
    // row with no correlation id could never be reconciled with anything.
    const started = await gateway.initiate({
      amountKes: amount,
      phoneNumber: dto.phoneNumber,
      reference,
      description: PROMPT_DESCRIPTION,
    });

    // Then the row, before the person has had time to enter a PIN. The result carries
    // nothing identifying the payer except this correlation id.
    await this.payments.recordPending({
      userId,
      provider: gateway.provider,
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
      provider: gateway.provider,
      paymentRef: started.gatewayRef,
      reference,
      plan: dto.plan,
      amount,
    };
  }

  /**
   * Where a payment got to, and the safety net for a webhook that never arrived.
   *
   * PalPluss stops retrying after five attempts and Daraja gives no guarantee at all,
   * so a payment can be complete at the gateway and pending here forever. When the
   * stored row is still pending this asks the gateway directly and, if the gateway says
   * the money moved, applies that answer through exactly the same path a webhook takes —
   * the same amount check, the same atomic claim, the same idempotency.
   */
  @Get(':paymentRef/status')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Check a payment, reconciling against the gateway if needed' })
  @ApiResponse({ status: 200, description: 'Current payment status' })
  @ApiResponse({ status: 404, description: 'No such payment for this user' })
  async status(
    @Param('paymentRef') paymentRef: string,
    @Req() req: { user?: { id?: string } },
  ) {
    const userId = req.user?.id as string;

    // Scoped to the caller: a payment reference is not a secret, and without this
    // anyone could watch anyone else's payments.
    const payment = await this.paymentRepository.findOne({
      where: { checkoutRequestId: paymentRef, userId },
    });
    if (!payment) {
      throw new NotFoundException('No such payment');
    }

    if (payment.status !== PaymentStatus.PENDING) {
      return this.describe(payment.status, payment);
    }

    const provider = asProvider(payment.provider);
    if (!provider) {
      this.logger.error(
        `Payment ${payment.id} records provider "${payment.provider}", which is not a ` +
          'known gateway; cannot reconcile',
      );
      return this.describe(payment.status, payment);
    }

    const live = await this.gateways.require(provider).fetchStatus(paymentRef);
    if (live.state === GatewayPaymentState.PENDING) {
      return this.describe(PaymentStatus.PENDING, payment);
    }

    // Through applyCallback, not a direct UPDATE: this path must not be a second, weaker
    // way to grant a subscription. A gateway that reports success for the wrong amount
    // is refused here exactly as it would be on the webhook.
    await this.payments.applyCallback(
      {
        provider,
        gatewayRef: paymentRef,
        success: live.state === GatewayPaymentState.SUCCEEDED,
        amountKes: live.amountKes,
        receipt: live.receipt,
        resultCode: /^-?\d+$/.test(live.resultCode ?? '') ? Number(live.resultCode) : null,
        resultDesc: live.resultDesc ?? 'Reconciled from the gateway',
      },
      { reconciledFrom: provider, at: new Date().toISOString() },
    );

    const refreshed = await this.paymentRepository.findOne({ where: { id: payment.id } });
    return this.describe(refreshed?.status ?? payment.status, refreshed ?? payment);
  }

  private describe(status: PaymentStatus, payment: Payment) {
    return {
      status,
      succeeded: status === PaymentStatus.SUCCEEDED,
      paymentRef: payment.checkoutRequestId,
      reference: payment.reference,
      plan: payment.plan,
      amount: payment.amountKes,
      receipt: payment.mpesaReceiptNumber,
      completedAt: payment.completedAt,
    };
  }

  /** Prices live in config so a change is not a deploy of the pricing screen too. */
  private priceFor(plan: string): number {
    const monthly = this.config.get<number>('subscription.monthlyPrice') ?? 500;
    const yearly = this.config.get<number>('subscription.yearlyPrice') ?? 5000;
    return plan === 'yearly' ? yearly : monthly;
  }
}

/** Re-exported so the module file does not need two imports to wire one controller. */
export { PaymentProvider };
