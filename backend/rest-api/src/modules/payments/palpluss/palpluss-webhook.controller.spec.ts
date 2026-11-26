import { BadRequestException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { createHmac } from 'node:crypto';
import { PaymentProvider } from '../payment-gateway';
import { CallbackOutcome, PaymentsService } from '../payments.service';
import {
  PalplussWebhookController,
  toGatewayCallback,
} from './palpluss-webhook.controller';

const SECRET = 'whsec_' + Buffer.from('ongero-webhook-controller-key').toString('base64');

function successBody(over: Record<string, unknown> = {}) {
  return {
    event: 'transaction.updated',
    event_type: 'transaction.success',
    transaction: {
      id: 'fa98a577-95ea-4a8f-8467-1fbe74f5d6f4',
      type: 'STK',
      status: 'SUCCESS',
      amount: 500,
      currency: 'KES',
      phone_number: '254712345678',
      external_reference: 'ONGB7K2M9XQ4',
      mpesa_receipt: 'LGR019G3J2',
      result_code: '0',
      result_desc: 'The service request is processed successfully.',
      ...over,
    },
  };
}

/**
 * Translating the vendor's wire shape is where a quiet mistake turns a cancelled
 * payment into a granted subscription, so success is asserted to require agreement
 * between all three fields that claim to describe the outcome.
 */
describe('toGatewayCallback', () => {
  it('reads a successful payment', () => {
    expect(toGatewayCallback(successBody(), 'tx-1')).toEqual({
      provider: PaymentProvider.PALPLUSS,
      gatewayRef: 'tx-1',
      success: true,
      amountKes: 500,
      receipt: 'LGR019G3J2',
      resultCode: 0,
      resultDesc: 'The service request is processed successfully.',
    });
  });

  it.each([
    ['transaction.failed', 'FAILED', '1'],
    ['transaction.cancelled', 'CANCELLED', '1032'],
    ['transaction.expired', 'EXPIRED', '1037'],
  ])('treats %s as a failure', (eventType, status, code) => {
    const body = { ...successBody({ status, result_code: code }), event_type: eventType };
    expect(toGatewayCallback(body, 'tx-1').success).toBe(false);
  });

  it('treats a status neither PalPluss document mentions as a failure', () => {
    // The OpenAPI enum and the webhook guide disagree: the first has REVERSED and no
    // EXPIRED, the second the reverse. Anything unrecognised must grant nothing.
    const body = successBody({ status: 'SOMETHING_NEW' });
    expect(toGatewayCallback(body, 'tx-1').success).toBe(false);
  });

  it('refuses a success whose result_code contradicts it', () => {
    // event_type and status both say it worked; M-Pesa's own code says insufficient
    // funds. Believing the headline here would hand out a subscription for nothing.
    const body = successBody({ result_code: '1' });
    expect(toGatewayCallback(body, 'tx-1').success).toBe(false);
  });

  it('still succeeds when result_code is absent entirely', () => {
    const body = successBody({ result_code: null });
    expect(toGatewayCallback(body, 'tx-1').success).toBe(true);
  });

  it('reports no amount rather than zero when the field is missing', () => {
    // Zero would be a number, and a number that fails the amount comparison for the
    // right reason by luck. null says "the gateway did not tell us".
    const body = successBody({ amount: undefined });
    expect(toGatewayCallback(body, 'tx-1').amountKes).toBeNull();
  });

  it('drops a non-integer result code instead of mangling it', () => {
    // payments.result_code is an integer column; Number('500.001.1001') is NaN and
    // parseInt would silently store 500, a completely different code.
    const body = successBody({ result_code: '500.001.1001' });
    expect(toGatewayCallback(body, 'tx-1').resultCode).toBeNull();
  });
});

describe('PalplussWebhookController', () => {
  let controller: PalplussWebhookController;
  let applyCallback: jest.Mock;

  async function build(secret: string) {
    applyCallback = jest.fn().mockResolvedValue(CallbackOutcome.ACTIVATED);
    const moduleRef = await Test.createTestingModule({
      controllers: [PalplussWebhookController],
      providers: [
        { provide: PaymentsService, useValue: { applyCallback } },
        { provide: ConfigService, useValue: { get: () => secret } },
      ],
    }).compile();
    controller = moduleRef.get(PalplussWebhookController);
  }

  const signed = (rawBody: Buffer, secret: string) => {
    const id = 'msg_test';
    const timestamp = String(Math.floor(Date.now() / 1000));
    const key = Buffer.from(secret.replace(/^whsec_/, ''), 'base64');
    const sig = createHmac('sha256', key)
      .update(Buffer.concat([Buffer.from(`${id}.${timestamp}.`, 'utf8'), rawBody]))
      .digest('base64');
    return {
      'webhook-id': id,
      'webhook-timestamp': timestamp,
      'webhook-signature': `v1,${sig}`,
    };
  };

  it('applies a correctly signed callback', async () => {
    await build(SECRET);
    const rawBody = Buffer.from(JSON.stringify(successBody()), 'utf8');

    await controller.handle({ rawBody, headers: signed(rawBody, SECRET) } as never);

    expect(applyCallback).toHaveBeenCalledTimes(1);
    expect(applyCallback.mock.calls[0][0]).toMatchObject({
      provider: PaymentProvider.PALPLUSS,
      success: true,
      amountKes: 500,
    });
  });

  it('refuses an unsigned callback when a secret is configured', async () => {
    await build(SECRET);
    const rawBody = Buffer.from(JSON.stringify(successBody()), 'utf8');

    await expect(
      controller.handle({ rawBody, headers: {} } as never),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(applyCallback).not.toHaveBeenCalled();
  });

  it('refuses a callback whose body was changed after signing', async () => {
    await build(SECRET);
    const rawBody = Buffer.from(JSON.stringify(successBody()), 'utf8');
    const headers = signed(rawBody, SECRET);
    const tampered = Buffer.from(
      JSON.stringify(successBody({ amount: 999999 })),
      'utf8',
    );

    await expect(
      controller.handle({ rawBody: tampered, headers } as never),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(applyCallback).not.toHaveBeenCalled();
  });

  it('accepts an unsigned callback when no secret is configured', async () => {
    // Not ideal, and logged as a warning at construction, but it is what PalPluss does
    // until the domain is registered. The amount and correlation checks in
    // PaymentsService are what stand in the way until then.
    await build('');
    const rawBody = Buffer.from(JSON.stringify(successBody()), 'utf8');

    await controller.handle({ rawBody, headers: {} } as never);

    expect(applyCallback).toHaveBeenCalledTimes(1);
  });

  it('refuses a request with no raw body rather than parsing the body twice', async () => {
    await build(SECRET);
    await expect(
      controller.handle({ headers: {} } as never),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('acknowledges the signed test event without applying anything', async () => {
    await build(SECRET);
    const rawBody = Buffer.from(JSON.stringify({ event: 'webhook.test' }), 'utf8');

    await expect(
      controller.handle({ rawBody, headers: signed(rawBody, SECRET) } as never),
    ).resolves.toEqual({ received: true });
    expect(applyCallback).not.toHaveBeenCalled();
  });

  it('refuses a signed body that is not JSON', async () => {
    await build(SECRET);
    const rawBody = Buffer.from('not json at all', 'utf8');

    await expect(
      controller.handle({ rawBody, headers: signed(rawBody, SECRET) } as never),
    ).rejects.toBeInstanceOf(BadRequestException);
  });
});
