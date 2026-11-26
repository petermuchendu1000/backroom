import { createHmac, timingSafeEqual } from 'node:crypto';

/**
 * Standard Webhooks signature verification — the scheme PalPluss uses, the same one as
 * Svix and Resend.
 *
 * This matters more here than it might elsewhere. The webhook endpoint has to be public
 * and it is the thing that grants paid access, so anyone who learns the URL can POST
 * "payment succeeded" at it. Daraja signs nothing, so the old callback had to defend
 * itself purely by refusing to recognise ids it never issued. PalPluss signs, so we can
 * refuse the forgery outright, before any of that logic runs.
 *
 * Three headers travel with each delivery:
 *
 *   webhook-id         msg_3f9a… — stable across retries of the same message
 *   webhook-timestamp  unix seconds, when THIS attempt was sent
 *   webhook-signature  "v1,<base64>", or several space-separated pairs for the 24 hours
 *                      after a secret rotation, when both the old and the new secret
 *                      sign each delivery
 *
 * The signature is base64(HMAC-SHA256(key, "{id}.{timestamp}.{body}")) where the key is
 * the base64-decoded remainder of the secret after the "whsec_" prefix.
 */

export const DEFAULT_TOLERANCE_SECONDS = 300;

export enum VerificationFailure {
  MISSING_HEADERS = 'missing_headers',
  BAD_TIMESTAMP = 'bad_timestamp',
  TIMESTAMP_OUT_OF_TOLERANCE = 'timestamp_out_of_tolerance',
  NO_MATCHING_SIGNATURE = 'no_matching_signature',
  MALFORMED_SECRET = 'malformed_secret',
}

export type VerificationResult =
  | { ok: true }
  | { ok: false; reason: VerificationFailure };

/**
 * Node's IncomingHttpHeaders, structurally. An index signature rather than three
 * optional keys, so an Express request's headers can be passed straight in: a type with
 * only optional properties is a "weak type" and TypeScript rejects assigning one that
 * shares no declared property with it.
 */
export type WebhookHeaders = Record<string, string | string[] | undefined>;

/**
 * @param rawBody the bytes exactly as received. NOT a re-serialised object: JSON.parse
 *   followed by JSON.stringify can change key order, whitespace and number formatting,
 *   and any of those changes the hash.
 */
export function verifyStandardWebhook(
  rawBody: Buffer,
  headers: WebhookHeaders,
  secret: string,
  now: Date = new Date(),
  toleranceSeconds: number = DEFAULT_TOLERANCE_SECONDS,
): VerificationResult {
  const id = single(headers['webhook-id']);
  const timestamp = single(headers['webhook-timestamp']);
  const signatures = single(headers['webhook-signature']);

  if (!id || !timestamp || !signatures) {
    return { ok: false, reason: VerificationFailure.MISSING_HEADERS };
  }

  // Number('') is 0 and Number(' 12 ') is 12, so neither is good enough on its own.
  if (!/^\d{1,15}$/.test(timestamp)) {
    return { ok: false, reason: VerificationFailure.BAD_TIMESTAMP };
  }

  // Both directions: an old delivery being replayed, and a future-dated one from a clock
  // that is wrong or a forger who picked a timestamp.
  const skew = Math.abs(Math.floor(now.getTime() / 1000) - Number(timestamp));
  if (skew > toleranceSeconds) {
    return { ok: false, reason: VerificationFailure.TIMESTAMP_OUT_OF_TOLERANCE };
  }

  const key = decodeSecret(secret);
  if (!key) {
    return { ok: false, reason: VerificationFailure.MALFORMED_SECRET };
  }

  const expected = createHmac('sha256', key)
    .update(Buffer.concat([Buffer.from(`${id}.${timestamp}.`, 'utf8'), rawBody]))
    .digest();

  // During a rotation two signatures arrive, separated by a space. Every candidate is
  // compared — short-circuiting on the first match is fine, the comparison itself is
  // what has to be constant time.
  for (const candidate of signatures.split(' ')) {
    const comma = candidate.indexOf(',');
    if (comma < 0) continue;
    if (candidate.slice(0, comma) !== 'v1') continue;

    const provided = Buffer.from(candidate.slice(comma + 1), 'base64');
    // timingSafeEqual throws on a length mismatch, so the lengths are checked first.
    // That leaks only the length of a hash whose length is fixed and public anyway.
    if (provided.length === expected.length && timingSafeEqual(provided, expected)) {
      return { ok: true };
    }
  }

  return { ok: false, reason: VerificationFailure.NO_MATCHING_SIGNATURE };
}

/**
 * A secret that decodes to nothing would make every signature check pass against an
 * empty key, so an unusable secret is a failure rather than a silently weak one.
 */
function decodeSecret(secret: string): Buffer | null {
  const body = secret.startsWith('whsec_') ? secret.slice('whsec_'.length) : secret;
  if (!body) return null;
  const key = Buffer.from(body, 'base64');
  return key.length > 0 ? key : null;
}

/** Node gives a repeated header as an array; a forger could repeat one deliberately. */
function single(value: string | string[] | undefined): string | null {
  if (typeof value === 'string') return value;
  if (Array.isArray(value) && value.length === 1) return value[0];
  return null;
}
