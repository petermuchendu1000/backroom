import { SubscriptionPlan } from '../../database/entities/payment.entity';

/**
 * Which gateway a payment went through.
 *
 * This is the gateway, not the rail. Both values below move money over M-Pesa; they
 * differ in who we send the request to and who sends us the result.
 */
export enum PaymentProvider {
  /** PalPluss (https://api.palpluss.com/v1), an aggregator that fronts Daraja. */
  PALPLUSS = 'palpluss',
  /** Safaricom Daraja, spoken to directly. */
  DARAJA = 'daraja',
}

/** Hard limits the gateway imposes on what we may send. */
export const REFERENCE_MAX_LENGTH = 12;
export const DESCRIPTION_MAX_LENGTH = 13;

export interface InitiateArgs {
  /** Whole shillings. Gateways reject fractions and amounts below 1. */
  amountKes: number;
  /** Whatever the person typed; the gateway normalises it. */
  phoneNumber: string;
  /** Our reference, at most REFERENCE_MAX_LENGTH characters. Echoed back to us. */
  reference: string;
  /** Shown on the PIN prompt, at most DESCRIPTION_MAX_LENGTH characters. */
  description: string;
}

export interface InitiateResult {
  /**
   * The id this gateway will quote when it reports the outcome, and therefore the only
   * thing that can tie that report back to the attempt. Stored as
   * payments.checkout_request_id and unique across all gateways.
   */
  gatewayRef: string;
  /** A second id, useful only when asking the gateway's support about a transaction. */
  providerRef: string | null;
  /** What to tell the person while they wait. */
  customerMessage: string;
}

/** Where a transaction has got to, as the gateway sees it right now. */
export enum GatewayPaymentState {
  PENDING = 'pending',
  SUCCEEDED = 'succeeded',
  FAILED = 'failed',
}

export interface GatewayStatus {
  state: GatewayPaymentState;
  /** Whole shillings the gateway says were actually paid, when it knows. */
  amountKes: number | null;
  receipt: string | null;
  resultCode: string | null;
  resultDesc: string | null;
}

/**
 * One way of taking money.
 *
 * Deliberately narrow. Everything about what a payment MEANS — who it is for, whether
 * the amount is the amount we asked for, what it buys, whether this result has already
 * been applied — lives in PaymentsService and is identical for every gateway. An
 * implementation of this interface only knows how to speak to one vendor.
 *
 * Results arrive asynchronously by webhook, which is why there is no `await payment`
 * here: initiate() returns as soon as the prompt has been sent, and fetchStatus() exists
 * for the case where the webhook never arrives.
 */
export interface PaymentGateway {
  readonly provider: PaymentProvider;

  /** Send the prompt. Throws HttpException on a refusal we can describe to the caller. */
  initiate(args: InitiateArgs): Promise<InitiateResult>;

  /**
   * Ask the gateway what actually happened. The authoritative answer, and the fallback
   * when a webhook was never delivered — PalPluss gives up after 5 attempts.
   */
  fetchStatus(gatewayRef: string): Promise<GatewayStatus>;
}

/**
 * A reference short enough for the gateways and unique enough to find on a statement.
 *
 * PalPluss caps accountReference at 12 characters, which rules out a user id: ours are
 * UUIDs. Daraja caps it at the same 12 and silently truncates instead of complaining,
 * which is worse. So: a fixed 3-character prefix and 9 characters of randomness, from an
 * alphabet with no vowels and no characters that are easy to confuse when read aloud
 * down a phone line to support.
 *
 * 9 characters of this 29-symbol alphabet is about 2^44 values. Collisions are not the
 * safety mechanism — checkout_request_id is UNIQUE and the gateway's own id is what
 * correlates a webhook — this is for humans reconciling statements.
 */
const REFERENCE_ALPHABET = '0123456789BCDFGHJKLMNPQRSTVWXZ';

export function generateReference(
  randomInt: (maxExclusive: number) => number = defaultRandomInt,
): string {
  let out = 'ONG';
  while (out.length < REFERENCE_MAX_LENGTH) {
    out += REFERENCE_ALPHABET[randomInt(REFERENCE_ALPHABET.length)];
  }
  return out;
}

function defaultRandomInt(maxExclusive: number): number {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { randomInt } = require('node:crypto') as typeof import('node:crypto');
  return randomInt(maxExclusive);
}

/**
 * What goes on the PIN prompt. 13 characters, so the plan does not fit: "Ongero monthly"
 * is 14 and PalPluss rejects it outright, while Daraja truncates it to "Ongero monthl"
 * and sends that to a real person's phone. The plan is recorded on the payment row
 * instead, where it is not competing for space.
 */
export const PROMPT_DESCRIPTION = 'Ongero Plus';

/** Plans exist so the caller can price them; the gateway never sees one. */
export type { SubscriptionPlan };
