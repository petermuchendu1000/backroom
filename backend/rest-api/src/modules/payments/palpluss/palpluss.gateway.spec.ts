import { HttpService } from '@nestjs/axios';
import { HttpException, HttpStatus } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { of, throwError } from 'rxjs';
import { GatewayPaymentState, generateReference } from '../payment-gateway';
import { PalplussGateway, mapState } from './palpluss.gateway';

const CONFIG: Record<string, string> = {
  'palpluss.baseUrl': 'https://api.palpluss.example/v1',
  'palpluss.apiKey': 'pp_test_abc123',
  'palpluss.callbackUrl': 'https://api.ongero.com/api/v1/payments/palpluss/webhook',
  'palpluss.channelId': '',
};

const OK_STK = {
  data: {
    success: true,
    requestId: 'req-1',
    data: {
      transactionId: 'fa98a577-95ea-4a8f-8467-1fbe74f5d6f4',
      status: 'PENDING',
      amount: 500,
      currency: 'KES',
      phone: '254712345678',
      providerRequestId: '29115-34620561-1',
      providerCheckoutId: 'ws_CO_191220191020363925',
      resultCode: '0',
      resultDescription: 'Success. Request accepted for processing',
    },
  },
};

async function build(over: Record<string, string> = {}, http: Partial<HttpService> = {}) {
  const config = { ...CONFIG, ...over };
  const moduleRef = await Test.createTestingModule({
    providers: [
      PalplussGateway,
      { provide: ConfigService, useValue: { get: (k: string) => config[k] } },
      { provide: HttpService, useValue: { post: jest.fn(), get: jest.fn(), ...http } },
    ],
  }).compile();
  return moduleRef.get(PalplussGateway);
}

const args = {
  amountKes: 500,
  phoneNumber: '0712345678',
  reference: 'ONGB7K2M9XQ4',
  description: 'Ongero Plus',
};

describe('PalplussGateway.initiate', () => {
  it('authenticates with base64(key + ":"), not the bare key', async () => {
    // The OpenAPI document's prose says to send the key unencoded. Every code sample
    // in the authentication guide — and the trailing colon it calls out explicitly —
    // says base64(key + ":"). Sending the bare key is a 401 on every request.
    const post = jest.fn().mockReturnValue(of(OK_STK));
    const gateway = await build({}, { post });

    await gateway.initiate(args);

    const [, , options] = post.mock.calls[0];
    expect(options.headers.Authorization).toBe(
      `Basic ${Buffer.from('pp_test_abc123:').toString('base64')}`,
    );
  });

  it('sends the documented field names and our callback URL', async () => {
    const post = jest.fn().mockReturnValue(of(OK_STK));
    const gateway = await build({}, { post });

    await gateway.initiate(args);

    const [url, body] = post.mock.calls[0];
    expect(url).toBe('https://api.palpluss.example/v1/payments/stk');
    expect(body).toEqual({
      amount: 500,
      phone: '0712345678',
      accountReference: 'ONGB7K2M9XQ4',
      transactionDesc: 'Ongero Plus',
      callbackUrl: CONFIG['palpluss.callbackUrl'],
    });
    // channelId is omitted rather than sent empty: PalPluss validates it as a uuid.
    expect(body).not.toHaveProperty('channelId');
  });

  it('includes channelId only when one is configured', async () => {
    const post = jest.fn().mockReturnValue(of(OK_STK));
    const gateway = await build(
      { 'palpluss.channelId': '8b3f1a2c-0000-0000-0000-000000000000' },
      { post },
    );

    await gateway.initiate(args);

    expect(post.mock.calls[0][1]).toMatchObject({
      channelId: '8b3f1a2c-0000-0000-0000-000000000000',
    });
  });

  it('returns the transaction id as the correlation key', async () => {
    const gateway = await build({}, { post: jest.fn().mockReturnValue(of(OK_STK)) });

    // Not providerCheckoutId: the webhook quotes transaction.id, so that is the only
    // value a result can be matched against.
    expect(await gateway.initiate(args)).toMatchObject({
      gatewayRef: 'fa98a577-95ea-4a8f-8467-1fbe74f5d6f4',
      providerRef: 'ws_CO_191220191020363925',
    });
  });

  it('refuses a 200 that carries success: false', async () => {
    // A 2xx with an error envelope would otherwise be read as an accepted payment,
    // and a pending row would be written for a prompt that was never sent.
    const post = jest.fn().mockReturnValue(
      of({ data: { success: false, error: { message: 'nope', code: 'X' }, requestId: 'r' } }),
    );
    const gateway = await build({}, { post });

    await expect(gateway.initiate(args)).rejects.toThrow(HttpException);
  });

  it('rejects a reference or description longer than the API allows', async () => {
    const post = jest.fn().mockReturnValue(of(OK_STK));
    const gateway = await build({}, { post });

    await expect(
      gateway.initiate({ ...args, reference: 'THIRTEEN_CHARS' }),
    ).rejects.toThrow(HttpException);
    await expect(
      gateway.initiate({ ...args, description: 'Ongero monthly' }),
    ).rejects.toThrow(HttpException);
    expect(post).not.toHaveBeenCalled();
  });

  it('refuses to call the API at all when it is not configured', async () => {
    const post = jest.fn();
    const gateway = await build({ 'palpluss.apiKey': '' }, { post });

    await expect(gateway.initiate(args)).rejects.toThrow(HttpException);
    expect(post).not.toHaveBeenCalled();
  });

  it('does not tell the customer that THEY have insufficient funds on a 402', async () => {
    // 402 means our service wallet is empty. Passing the vendor's wording through
    // would send the customer off to top up an account that is already funded.
    const post = jest
      .fn()
      .mockReturnValue(throwError(() => ({ response: { status: 402, data: {} } })));
    const gateway = await build({}, { post });

    await expect(gateway.initiate(args)).rejects.toMatchObject({
      status: HttpStatus.SERVICE_UNAVAILABLE,
    });
  });

  it('maps a bad phone number to a 400 the caller can show', async () => {
    const post = jest.fn().mockReturnValue(
      throwError(() => ({
        response: {
          status: 400,
          data: { success: false, error: { code: 'INVALID_PHONE', message: 'Invalid phone' } },
        },
      })),
    );
    const gateway = await build({}, { post });

    await expect(gateway.initiate(args)).rejects.toMatchObject({
      status: HttpStatus.BAD_REQUEST,
    });
  });

  it('reports a gateway outage as unavailable, not as the customer\'s fault', async () => {
    const post = jest
      .fn()
      .mockReturnValue(throwError(() => ({ response: { status: 500, data: {} } })));
    const gateway = await build({}, { post });

    await expect(gateway.initiate(args)).rejects.toMatchObject({
      status: HttpStatus.SERVICE_UNAVAILABLE,
    });
  });
});

describe('PalplussGateway.fetchStatus', () => {
  it('reads a settled transaction', async () => {
    const get = jest.fn().mockReturnValue(
      of({
        data: {
          success: true,
          requestId: 'r',
          data: {
            transactionId: 'tx-1',
            status: 'SUCCESS',
            amount: 500,
            mpesaReceipt: 'LGR019G3J2',
            resultCode: '0',
            resultDesc: 'ok',
          },
        },
      }),
    );
    const gateway = await build({}, { get });

    expect(await gateway.fetchStatus('tx-1')).toEqual({
      state: GatewayPaymentState.SUCCEEDED,
      amountKes: 500,
      receipt: 'LGR019G3J2',
      resultCode: '0',
      resultDesc: 'ok',
    });
    expect(get.mock.calls[0][0]).toBe('https://api.palpluss.example/v1/transactions/tx-1');
  });
});

describe('mapState', () => {
  it('treats only SUCCESS as money having moved', () => {
    expect(mapState('SUCCESS')).toBe(GatewayPaymentState.SUCCEEDED);
    expect(mapState('success')).toBe(GatewayPaymentState.SUCCEEDED);
  });

  it('treats the in-flight states as pending', () => {
    expect(mapState('PENDING')).toBe(GatewayPaymentState.PENDING);
    expect(mapState('PROCESSING')).toBe(GatewayPaymentState.PENDING);
  });

  it.each(['FAILED', 'CANCELLED', 'REVERSED', 'EXPIRED', 'WHO_KNOWS', '', null, undefined])(
    'treats %s as a failure',
    (status) => {
      expect(mapState(status)).toBe(GatewayPaymentState.FAILED);
    },
  );
});

describe('generateReference', () => {
  it('fits inside the 12 characters PalPluss allows', () => {
    for (let i = 0; i < 200; i++) {
      expect(generateReference()).toHaveLength(12);
    }
  });

  it('is prefixed so it is recognisable on a statement', () => {
    expect(generateReference()).toMatch(/^ONG[0-9BCDFGHJKLMNPQRSTVWXZ]{9}$/);
  });

  it('does not repeat itself', () => {
    const seen = new Set(Array.from({ length: 500 }, () => generateReference()));
    expect(seen.size).toBe(500);
  });
});
