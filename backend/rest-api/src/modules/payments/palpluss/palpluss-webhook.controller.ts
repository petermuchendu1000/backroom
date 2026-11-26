import {
  BadRequestException,
  Controller,
  HttpCode,
  HttpStatus,
  Logger,
  Post,
  RawBodyRequest,
  Req,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiExcludeEndpoint, ApiOperation, ApiTags } from '@nestjs/swagger';
import type { Request } from 'express';
import { PaymentProvider } from '../payment-gateway';
import { CallbackOutcome, GatewayCallback, PaymentsService } from '../payments.service';
import { verifyStandardWebhook } from './standard-webhooks';

/** The body PalPluss POSTs when a transaction reaches a terminal state. */
interface PalplussWebhookBody {
  event?: string;
  event_type?: string;
  transaction?: {
    id?: string;
    type?: string;
    status?: string;
    amount?: number | string;
    currency?: string;
    phone_number?: string;
    external_reference?: string;
    mpesa_receipt?: string | null;
    result_code?: string | null;
    result_desc?: string | null;
  };
}

/**
 * Where PalPluss reports what happened.
 *
 * Necessarily unauthenticated — PalPluss has no credential of ours to present — so the
 * defences are, in order:
 *
 *   1. the Standard Webhooks signature, when a signing secret is configured;
 *   2. the correlation id having been issued by us, through this gateway;
 *   3. the amount paid being the amount we asked for.
 *
 * Only the first is new. Two and three live in PaymentsService and run for every
 * gateway, signed or not, because a signature proves who sent a message and nothing at
 * all about whether its contents should grant a subscription.
 */
@ApiTags('payments')
@Controller('payments/palpluss')
export class PalplussWebhookController {
  private readonly logger = new Logger(PalplussWebhookController.name);
  private readonly secret: string;

  constructor(
    private readonly payments: PaymentsService,
    config: ConfigService,
  ) {
    this.secret = config.get<string>('palpluss.webhookSecret') ?? '';
    if (!this.secret) {
      // Loud, because it is a real reduction in safety and because the fix is a console
      // setting plus an environment variable, not a code change. PalPluss delivers
      // callbacks to domains that have not been registered for signing — unsigned.
      this.logger.warn(
        'PALPLUSS_WEBHOOK_SECRET is not set: PalPluss callbacks will be accepted ' +
          'WITHOUT a signature check. Add the callback domain under Webhooks in the ' +
          'PalPluss console and set the whsec_ secret to close this.',
      );
    }
  }

  @Post('webhook')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'PalPluss transaction result webhook' })
  @ApiExcludeEndpoint()
  async handle(@Req() req: RawBodyRequest<Request>) {
    // The signature is over the bytes as received. Re-serialising the parsed object can
    // change key order, spacing or number formatting, and any of those breaks the hash.
    // req.rawBody exists because main.ts creates the app with rawBody: true.
    const rawBody = req.rawBody;
    if (!rawBody) {
      this.logger.error(
        'No raw body on a PalPluss webhook: the app was not created with rawBody: true',
      );
      throw new BadRequestException('Malformed request');
    }

    if (!this.secret) {
      // Every delivery, not just a line at boot. With no secret this endpoint will
      // settle any pending payment whose transaction id the sender can name, and that
      // is worth seeing in the logs for as long as it is true.
      this.logger.error(
        'Accepting a PalPluss callback with NO signature check (PALPLUSS_WEBHOOK_SECRET unset)',
      );
    } else {
      const verdict = verifyStandardWebhook(rawBody, req.headers, this.secret);
      if (!verdict.ok) {
        // 400 is what PalPluss's own guidance says to return. It counts as a failed
        // delivery and will be retried, which is right: a transient clock skew should
        // get another chance, and a forgery simply fails four more times.
        this.logger.error(`Rejected a PalPluss webhook: ${verdict.reason}`);
        throw new BadRequestException('Invalid signature');
      }
    }

    let body: PalplussWebhookBody;
    try {
      body = JSON.parse(rawBody.toString('utf8')) as PalplussWebhookBody;
    } catch {
      throw new BadRequestException('Malformed JSON');
    }

    const gatewayRef = body.transaction?.id;
    if (!gatewayRef) {
      // Includes the signed webhook.test event, which carries no transaction.
      this.logger.log(`PalPluss webhook with no transaction id (event=${body.event_type})`);
      return { received: true };
    }

    const callback = toGatewayCallback(body, gatewayRef);
    const outcome = await this.payments.applyCallback(
      callback,
      body as unknown as Record<string, unknown>,
    );

    switch (outcome) {
      case CallbackOutcome.ACTIVATED:
        this.logger.log(`Subscription activated for PalPluss transaction ${gatewayRef}`);
        break;
      case CallbackOutcome.ALREADY_APPLIED:
        this.logger.log(`Duplicate PalPluss webhook for ${gatewayRef} — ignored`);
        break;
      case CallbackOutcome.UNKNOWN_CHECKOUT:
        this.logger.warn(`PalPluss webhook for unknown transaction ${gatewayRef}`);
        break;
      case CallbackOutcome.AMOUNT_MISMATCH:
        this.logger.error(`Amount mismatch on PalPluss transaction ${gatewayRef}`);
        break;
      case CallbackOutcome.PAYMENT_FAILED:
        this.logger.warn(
          `PalPluss transaction ${gatewayRef} did not succeed: ${callback.resultDesc}`,
        );
        break;
    }

    // 2xx acknowledges delivery. An outcome of "ignored" is still a delivery; retrying
    // it would change nothing. An exception thrown above is a 5xx and WILL be retried,
    // which is what we want if our own database was briefly unavailable.
    return { received: true };
  }
}

/**
 * Translate the vendor's wire shape into the one PaymentsService understands.
 *
 * Success requires agreement, not any single field. event_type is the vendor's
 * headline, status is the transaction's own state, and result_code is M-Pesa's. The two
 * PalPluss documents disagree about the status vocabulary (the OpenAPI enum has no
 * EXPIRED; the webhook guide has no REVERSED), so a value that is not recognisably a
 * success is treated as a failure, and a result_code that contradicts a claimed success
 * overrides it.
 */
export function toGatewayCallback(
  body: PalplussWebhookBody,
  gatewayRef: string,
): GatewayCallback {
  const tx = body.transaction ?? {};
  const resultCode = tx.result_code ?? null;

  const claimsSuccess =
    body.event_type === 'transaction.success' &&
    (tx.status ?? '').toUpperCase() === 'SUCCESS';
  const codeContradicts = resultCode !== null && resultCode !== '' && resultCode !== '0';

  const amount = Number(tx.amount);

  return {
    provider: PaymentProvider.PALPLUSS,
    gatewayRef,
    success: claimsSuccess && !codeContradicts,
    amountKes: Number.isFinite(amount) ? Math.round(amount) : null,
    receipt: tx.mpesa_receipt ?? null,
    // payments.result_code is an integer column. Daraja-style codes such as
    // "500.001.1001" are not integers, so they are dropped rather than rounded into
    // something that looks like a different code; result_desc keeps the detail.
    resultCode: /^-?\d+$/.test(resultCode ?? '') ? Number(resultCode) : null,
    resultDesc: tx.result_desc ?? body.event_type ?? 'No description supplied',
  };
}
