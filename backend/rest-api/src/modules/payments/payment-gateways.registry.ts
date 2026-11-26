import { HttpException, HttpStatus, Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DarajaGateway } from './daraja.gateway';
import { PalplussGateway } from './palpluss/palpluss.gateway';
import { PaymentGateway, PaymentProvider } from './payment-gateway';

/**
 * The gateways this deployment can take money through, and which one it reaches for.
 *
 * There is a default rather than a per-request choice because the person paying has no
 * way to know or care which aggregator we use — they see one M-Pesa prompt either way.
 * Resolution by name exists so that an INCOMING webhook can find the gateway that sent
 * it, and so switching provider is an environment variable rather than a deploy.
 */
@Injectable()
export class PaymentGatewayRegistry implements OnModuleInit {
  private readonly logger = new Logger(PaymentGatewayRegistry.name);
  private readonly gateways = new Map<PaymentProvider, PaymentGateway>();
  private readonly defaultProvider: PaymentProvider;

  constructor(
    config: ConfigService,
    palpluss: PalplussGateway,
    daraja: DarajaGateway,
  ) {
    for (const gateway of [palpluss, daraja]) {
      this.gateways.set(gateway.provider, gateway);
    }

    const configured = config.get<string>('payments.provider');
    this.defaultProvider = asProvider(configured) ?? PaymentProvider.PALPLUSS;
    if (configured && !asProvider(configured)) {
      // Not a throw: refusing to boot over a typo in an optional variable would take the
      // whole API down, including the parts that have nothing to do with payments.
      this.logger.error(
        `PAYMENTS_PROVIDER="${configured}" is not a known gateway ` +
          `(${Object.values(PaymentProvider).join(', ')}); falling back to ` +
          `${PaymentProvider.PALPLUSS}`,
      );
    }
  }

  onModuleInit(): void {
    this.logger.log(
      `Payment gateways available: ${[...this.gateways.keys()].join(', ')}; ` +
        `default ${this.defaultProvider}`,
    );
  }

  /** The gateway a new payment should go through. */
  default(): PaymentGateway {
    return this.require(this.defaultProvider);
  }

  get defaultName(): PaymentProvider {
    return this.defaultProvider;
  }

  require(provider: PaymentProvider): PaymentGateway {
    const gateway = this.gateways.get(provider);
    if (!gateway) {
      throw new HttpException(
        'Payment service is not configured',
        HttpStatus.SERVICE_UNAVAILABLE,
      );
    }
    return gateway;
  }
}

/** Narrow an untrusted string to a provider, or null. Never throws. */
export function asProvider(value: string | null | undefined): PaymentProvider | null {
  const match = Object.values(PaymentProvider).find((p) => p === value);
  return match ?? null;
}
