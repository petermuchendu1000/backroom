import { Injectable, Logger } from '@nestjs/common';
import { MpesaService } from '../mpesa/mpesa.service';
import {
  GatewayPaymentState,
  GatewayStatus,
  InitiateArgs,
  InitiateResult,
  PaymentGateway,
  PaymentProvider,
} from './payment-gateway';

/**
 * Safaricom Daraja, spoken to directly, behind the same interface as PalPluss.
 *
 * This is a thin adapter over the MpesaService that was already here, kept for two
 * reasons: it is a working fallback if PalPluss is down or an account problem blocks us,
 * and having a second implementation is what proves the interface is actually an
 * interface rather than PalPluss's shape with a different name on it.
 *
 * It is NOT equivalent to PalPluss in one respect that matters: Daraja callbacks are
 * unsigned, so a deployment that selects this gateway is back to defending its public
 * callback endpoint with correlation and amount checks alone.
 */
@Injectable()
export class DarajaGateway implements PaymentGateway {
  readonly provider = PaymentProvider.DARAJA;

  private readonly logger = new Logger(DarajaGateway.name);

  constructor(private readonly mpesa: MpesaService) {}

  async initiate(args: InitiateArgs): Promise<InitiateResult> {
    const response = await this.mpesa.initiateSTKPush({
      phoneNumber: args.phoneNumber,
      amount: args.amountKes,
      accountReference: args.reference,
      transactionDesc: args.description,
    });

    this.logger.log(
      `Daraja STK accepted: ${response.CheckoutRequestID} (ref ${args.reference})`,
    );

    return {
      gatewayRef: response.CheckoutRequestID,
      providerRef: response.MerchantRequestID ?? null,
      customerMessage:
        response.CustomerMessage || 'Please check your phone and enter your M-Pesa PIN',
    };
  }

  async fetchStatus(gatewayRef: string): Promise<GatewayStatus> {
    const status = await this.mpesa.querySTKPushStatus(gatewayRef);
    const code = status?.ResultCode !== undefined ? String(status.ResultCode) : null;

    // Daraja's query reports an outcome, not an amount, and never a receipt — those
    // only ever arrive on the callback. Saying null is honest; inventing 0 would let a
    // caller conclude the amount did not match.
    return {
      state: darajaState(code),
      amountKes: null,
      receipt: null,
      resultCode: code,
      resultDesc: status?.ResultDesc ?? null,
    };
  }
}

/**
 * Daraja result codes: "0" is success. 1032 is the customer cancelling and 1037 a
 * timeout, both terminal. 1037 can also appear while a prompt is still on screen, so
 * the only state treated as still-in-flight is the one Daraja uses for it explicitly.
 */
export function darajaState(resultCode: string | null): GatewayPaymentState {
  if (resultCode === null || resultCode === '') return GatewayPaymentState.PENDING;
  if (resultCode === '0') return GatewayPaymentState.SUCCEEDED;
  // 500.001.1001: "a transaction is already in process for the current subscriber".
  if (resultCode === '500.001.1001') return GatewayPaymentState.PENDING;
  return GatewayPaymentState.FAILED;
}
