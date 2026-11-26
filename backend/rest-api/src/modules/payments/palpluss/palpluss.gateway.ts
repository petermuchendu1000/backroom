import { HttpService } from '@nestjs/axios';
import { HttpException, HttpStatus, Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AxiosError } from 'axios';
import { firstValueFrom } from 'rxjs';
import {
  DESCRIPTION_MAX_LENGTH,
  GatewayPaymentState,
  GatewayStatus,
  InitiateArgs,
  InitiateResult,
  PaymentGateway,
  PaymentProvider,
  REFERENCE_MAX_LENGTH,
} from '../payment-gateway';

/** Everything PalPluss wraps a response in. */
interface SuccessEnvelope<T> {
  success: true;
  data: T;
  requestId: string;
}

interface ErrorEnvelope {
  success: false;
  error: { message: string; code: string; details?: unknown };
  requestId: string;
}

interface StkInitiateResponse {
  transactionId: string;
  status: string;
  amount: number;
  currency: string;
  phone: string;
  providerRequestId: string | null;
  providerCheckoutId: string | null;
  resultCode: string | null;
  resultDescription: string | null;
}

interface TransactionResponse {
  transactionId: string;
  status: string;
  amount: number;
  resultCode: string | null;
  resultDesc: string | null;
  mpesaReceipt?: string | null;
}

/**
 * PalPluss — an aggregator that fronts Safaricom Daraja.
 *
 * What it buys us over talking to Daraja directly: no OAuth token dance, no passkey or
 * shortcode in our config, and — the reason that actually matters — SIGNED callbacks.
 * Daraja signs nothing, so the direct integration can only defend its public callback
 * endpoint by refusing ids it never issued. See standard-webhooks.ts.
 *
 * Contract source: https://docs.palpluss.com/openapi.yaml (OpenAPI 3.1, title
 * "PalPluss API", server https://api.palpluss.com/v1).
 */
@Injectable()
export class PalplussGateway implements PaymentGateway {
  readonly provider = PaymentProvider.PALPLUSS;

  private readonly logger = new Logger(PalplussGateway.name);
  private readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly callbackUrl: string;
  private readonly channelId: string | null;

  constructor(
    private readonly config: ConfigService,
    private readonly http: HttpService,
  ) {
    this.baseUrl = (
      this.config.get<string>('palpluss.baseUrl') ?? 'https://api.palpluss.com/v1'
    ).replace(/\/+$/, '');
    this.apiKey = this.config.get<string>('palpluss.apiKey') ?? '';
    this.callbackUrl = this.config.get<string>('palpluss.callbackUrl') ?? '';
    this.channelId = this.config.get<string>('palpluss.channelId') || null;
  }

  /**
   * Basic auth with the API key as the username and an EMPTY password — note the
   * trailing colon. The OpenAPI document's own prose says to send the key unencoded
   * ("Authorization: Basic YOUR_API_KEY"); that contradicts the authentication guide and
   * every one of PalPluss's five code samples, which agree on base64(key + ":").
   * The samples win.
   *
   * The key is never logged. It is read from PALPLUSS_API_KEY.
   */
  private authHeader(): string {
    return `Basic ${Buffer.from(`${this.apiKey}:`).toString('base64')}`;
  }

  /** Fail where it can be understood, rather than with a 401 from the vendor. */
  private assertConfigured(): void {
    const missing: string[] = [];
    if (!this.apiKey) missing.push('PALPLUSS_API_KEY');
    if (!this.callbackUrl) missing.push('PALPLUSS_CALLBACK_URL');
    if (missing.length) {
      this.logger.error(`PalPluss is not configured: ${missing.join(', ')} unset`);
      throw new HttpException(
        'Payment service is not configured',
        HttpStatus.SERVICE_UNAVAILABLE,
      );
    }
  }

  async initiate(args: InitiateArgs): Promise<InitiateResult> {
    this.assertConfigured();

    // The API enforces these as maxLength and rejects anything longer, so a caller that
    // gets it wrong should find out here and not in a 400 from the vendor.
    if (args.reference.length > REFERENCE_MAX_LENGTH) {
      throw new HttpException(
        `Payment reference may not exceed ${REFERENCE_MAX_LENGTH} characters`,
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
    if (args.description.length > DESCRIPTION_MAX_LENGTH) {
      throw new HttpException(
        `Payment description may not exceed ${DESCRIPTION_MAX_LENGTH} characters`,
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }

    const amount = Math.round(args.amountKes);
    if (!Number.isFinite(amount) || amount < 1) {
      throw new HttpException('Invalid payment amount', HttpStatus.BAD_REQUEST);
    }

    const body: Record<string, unknown> = {
      amount,
      // Sent as given. PalPluss accepts 07…, 254… and +254… and normalises to 254…,
      // so re-implementing that here would only add a second thing to get wrong.
      phone: args.phoneNumber,
      accountReference: args.reference,
      transactionDesc: args.description,
      callbackUrl: this.callbackUrl,
    };
    if (this.channelId) body.channelId = this.channelId;

    const data = await this.post<StkInitiateResponse>('/payments/stk', body);

    this.logger.log(
      `PalPluss STK accepted: transaction ${data.transactionId} ` +
        `(ref ${args.reference}, ${amount} KES)`,
    );

    return {
      gatewayRef: data.transactionId,
      providerRef: data.providerCheckoutId ?? data.providerRequestId ?? null,
      customerMessage: 'Please check your phone and enter your M-Pesa PIN',
    };
  }

  async fetchStatus(gatewayRef: string): Promise<GatewayStatus> {
    this.assertConfigured();
    const data = await this.get<TransactionResponse>(
      `/transactions/${encodeURIComponent(gatewayRef)}`,
    );
    return {
      state: mapState(data.status),
      amountKes: Number.isFinite(Number(data.amount)) ? Math.round(Number(data.amount)) : null,
      receipt: data.mpesaReceipt ?? null,
      resultCode: data.resultCode ?? null,
      resultDesc: data.resultDesc ?? null,
    };
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    return this.call<T>(() =>
      firstValueFrom(
        this.http.post<SuccessEnvelope<T>>(`${this.baseUrl}${path}`, body, {
          headers: { Authorization: this.authHeader(), 'Content-Type': 'application/json' },
        }),
      ),
    );
  }

  private async get<T>(path: string): Promise<T> {
    return this.call<T>(() =>
      firstValueFrom(
        this.http.get<SuccessEnvelope<T>>(`${this.baseUrl}${path}`, {
          headers: { Authorization: this.authHeader() },
        }),
      ),
    );
  }

  private async call<T>(send: () => Promise<{ data: SuccessEnvelope<T> }>): Promise<T> {
    let response: { data: SuccessEnvelope<T> };
    try {
      response = await send();
    } catch (error) {
      throw this.translate(error);
    }

    // A 200 carrying success:false would otherwise be read as a successful payment.
    const envelope = response.data;
    if (!envelope || envelope.success !== true || envelope.data === undefined) {
      this.logger.error(
        `PalPluss returned a 2xx without a success envelope: ${safeShape(envelope)}`,
      );
      throw new HttpException('Payment initiation failed', HttpStatus.BAD_GATEWAY);
    }
    return envelope.data;
  }

  /**
   * Turn a vendor error into something the caller can act on, without leaking the
   * vendor's wording to the person paying and without logging the Authorization header.
   */
  private translate(error: unknown): HttpException {
    const axiosError = error as AxiosError<ErrorEnvelope>;
    const status = axiosError.response?.status;
    const envelope = axiosError.response?.data;
    const code = envelope?.error?.code;
    const requestId = envelope?.requestId;

    this.logger.error(
      `PalPluss call failed: http=${status ?? 'none'} code=${code ?? 'none'} ` +
        `requestId=${requestId ?? 'none'} message=${envelope?.error?.message ?? axiosError.message}`,
    );

    // 402 means OUR service wallet is empty, not that the customer cannot pay. Saying
    // "insufficient funds" to the customer would be a lie and they would go top up.
    if (status === 402) {
      return new HttpException(
        'Payment service is temporarily unavailable',
        HttpStatus.SERVICE_UNAVAILABLE,
      );
    }
    if (status === 401 || status === 403) {
      return new HttpException(
        'Payment service is not configured',
        HttpStatus.SERVICE_UNAVAILABLE,
      );
    }
    if (code === 'INVALID_PHONE') {
      return new HttpException(
        'That phone number is not a valid M-Pesa number',
        HttpStatus.BAD_REQUEST,
      );
    }
    if (status && status >= 400 && status < 500) {
      return new HttpException('Payment could not be started', HttpStatus.BAD_REQUEST);
    }
    return new HttpException(
      'Payment service is temporarily unavailable',
      HttpStatus.SERVICE_UNAVAILABLE,
    );
  }
}

/**
 * SUCCESS is the only value that means money moved.
 *
 * The two PalPluss documents disagree about the vocabulary: the OpenAPI enum is
 * PENDING/PROCESSING/SUCCESS/FAILED/REVERSED/CANCELLED, while the webhook guide lists
 * SUCCESS/FAILED/CANCELLED/EXPIRED. Rather than pick one and switch exhaustively over
 * it, anything that is not recognisably in flight and is not SUCCESS is a failure. A
 * value neither document mentions therefore grants nothing.
 */
export function mapState(status: string | null | undefined): GatewayPaymentState {
  switch ((status ?? '').toUpperCase()) {
    case 'SUCCESS':
      return GatewayPaymentState.SUCCEEDED;
    case 'PENDING':
    case 'PROCESSING':
      return GatewayPaymentState.PENDING;
    default:
      return GatewayPaymentState.FAILED;
  }
}

/** Log the shape of an unexpected body, never its contents. */
function safeShape(value: unknown): string {
  if (value === null || value === undefined) return String(value);
  if (typeof value !== 'object') return typeof value;
  return `{${Object.keys(value as object).sort().join(',')}}`;
}
