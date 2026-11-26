import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import {
  Payment,
  PaymentStatus,
  SubscriptionPlan,
} from '../../database/entities/payment.entity';
import { Subscription } from '../../database/entities/subscription.entity';
import { SubscriptionTier, User } from '../../database/entities/user.entity';
import {
  CallbackOutcome,
  CallbackResult,
  PaymentsService,
  expiryFor,
} from './payments.service';

/**
 * The callback endpoint is public and Safaricom signs nothing, so these tests are
 * mostly about what must NOT happen: a stranger's POST, a replay, or a short payment
 * must never grant a subscription.
 */

const CHECKOUT = 'ws_CO_01012026123456789';

function successCallback(amount: number | string = 500): CallbackResult {
  return {
    success: true,
    checkoutRequestId: CHECKOUT,
    merchantRequestId: 'mr-1',
    resultCode: 0,
    resultDesc: 'The service request is processed successfully.',
    metadata: { Amount: amount, MpesaReceiptNumber: 'QBR1A2B3C4', PhoneNumber: 254712345678 },
  };
}

function pendingPayment(over: Partial<Payment> = {}): Payment {
  return {
    id: 'pay-1',
    userId: 'user-1',
    checkoutRequestId: CHECKOUT,
    merchantRequestId: 'mr-1',
    plan: SubscriptionPlan.MONTHLY,
    amountKes: 500,
    phoneNumber: '0712345678',
    status: PaymentStatus.PENDING,
    ...over,
  } as Payment;
}

describe('PaymentsService.applyCallback', () => {
  let service: PaymentsService;
  let findOne: jest.Mock;
  let execute: jest.Mock;
  let txExecute: jest.Mock;
  let subscriptionSave: jest.Mock;
  let userUpdate: jest.Mock;

  /** Chainable stand-in for createQueryBuilder().update().set().where().execute(). */
  const builder = (exec: jest.Mock) => ({
    update: jest.fn().mockReturnThis(),
    set: jest.fn().mockReturnThis(),
    where: jest.fn().mockReturnThis(),
    execute: exec,
  });

  beforeEach(async () => {
    findOne = jest.fn();
    execute = jest.fn().mockResolvedValue({ affected: 1 });
    txExecute = jest.fn().mockResolvedValue({ affected: 1 });
    subscriptionSave = jest.fn().mockResolvedValue({});
    userUpdate = jest.fn().mockResolvedValue({});

    const manager = {
      createQueryBuilder: () => builder(txExecute),
      getRepository: (entity: unknown) => {
        if (entity === Subscription) {
          return { create: (x: unknown) => x, save: subscriptionSave };
        }
        if (entity === User) {
          return { update: userUpdate };
        }
        throw new Error('unexpected repository');
      },
    };

    const moduleRef = await Test.createTestingModule({
      providers: [
        PaymentsService,
        {
          provide: getRepositoryToken(Payment),
          useValue: {
            findOne,
            create: (x: unknown) => x,
            save: jest.fn(async (x) => x),
            createQueryBuilder: () => builder(execute),
          },
        },
        {
          provide: DataSource,
          useValue: {
            transaction: (cb: (m: unknown) => Promise<unknown>) => cb(manager),
          },
        },
      ],
    }).compile();

    service = moduleRef.get(PaymentsService);
  });

  it('grants nothing for a CheckoutRequestID we never issued', async () => {
    findOne.mockResolvedValue(null);

    const outcome = await service.applyCallback(successCallback());

    expect(outcome).toBe(CallbackOutcome.UNKNOWN_CHECKOUT);
    expect(subscriptionSave).not.toHaveBeenCalled();
    expect(userUpdate).not.toHaveBeenCalled();
  });

  it('activates a subscription for a genuine successful payment', async () => {
    findOne.mockResolvedValue(pendingPayment());

    const outcome = await service.applyCallback(successCallback());

    expect(outcome).toBe(CallbackOutcome.ACTIVATED);
    expect(userUpdate).toHaveBeenCalledWith(
      { id: 'user-1' },
      { subscriptionTier: SubscriptionTier.PLUS },
    );

    // The subscription row itself, not just the tier on the user: a mutation that
    // granted PREMIUM here went undetected until this was asserted.
    expect(subscriptionSave).toHaveBeenCalledTimes(1);
    const saved = subscriptionSave.mock.calls[0][0];
    expect(saved).toMatchObject({
      userId: 'user-1',
      provider: 'mpesa',
      providerSubscriptionId: CHECKOUT,
      tier: SubscriptionTier.PLUS,
      isActive: true,
    });
    expect(saved.expiresAt.getTime()).toBeGreaterThan(saved.startedAt.getTime());
  });

  it('does not activate twice when the same callback is replayed', async () => {
    findOne.mockResolvedValue(pendingPayment({ status: PaymentStatus.SUCCEEDED }));

    const outcome = await service.applyCallback(successCallback());

    expect(outcome).toBe(CallbackOutcome.ALREADY_APPLIED);
    expect(subscriptionSave).not.toHaveBeenCalled();
  });

  it('does not activate when a concurrent callback claimed the row first', async () => {
    // Both callers read 'pending'; only one UPDATE matches.
    findOne.mockResolvedValue(pendingPayment());
    txExecute.mockResolvedValue({ affected: 0 });

    const outcome = await service.applyCallback(successCallback());

    expect(outcome).toBe(CallbackOutcome.ALREADY_APPLIED);
    expect(subscriptionSave).not.toHaveBeenCalled();
    expect(userUpdate).not.toHaveBeenCalled();
  });

  it('does not activate when less was paid than was asked for', async () => {
    findOne.mockResolvedValue(pendingPayment({ amountKes: 5000 }));

    const outcome = await service.applyCallback(successCallback(1));

    expect(outcome).toBe(CallbackOutcome.AMOUNT_MISMATCH);
    expect(subscriptionSave).not.toHaveBeenCalled();
    expect(userUpdate).not.toHaveBeenCalled();
  });

  it('does not activate when the callback carries no amount at all', async () => {
    findOne.mockResolvedValue(pendingPayment());

    const outcome = await service.applyCallback({
      ...successCallback(),
      metadata: {},
    });

    expect(outcome).toBe(CallbackOutcome.AMOUNT_MISMATCH);
    expect(subscriptionSave).not.toHaveBeenCalled();
  });

  it('records a cancelled payment without granting anything', async () => {
    findOne.mockResolvedValue(pendingPayment());

    const outcome = await service.applyCallback({
      success: false,
      checkoutRequestId: CHECKOUT,
      merchantRequestId: 'mr-1',
      resultCode: 1032,
      resultDesc: 'Request cancelled by user',
    });

    expect(outcome).toBe(CallbackOutcome.PAYMENT_FAILED);
    expect(execute).toHaveBeenCalled(); // marked terminal
    expect(subscriptionSave).not.toHaveBeenCalled();
    expect(userUpdate).not.toHaveBeenCalled();
  });
});

describe('expiryFor', () => {
  it('gives a month for the monthly plan', () => {
    const from = new Date('2026-01-15T10:00:00.000Z');
    expect(expiryFor(SubscriptionPlan.MONTHLY, from).toISOString()).toBe(
      '2026-02-15T10:00:00.000Z',
    );
  });

  it('gives a year for the yearly plan', () => {
    const from = new Date('2026-01-15T10:00:00.000Z');
    expect(expiryFor(SubscriptionPlan.YEARLY, from).toISOString()).toBe(
      '2027-01-15T10:00:00.000Z',
    );
  });

  it('does not mutate the date it was given', () => {
    const from = new Date('2026-01-15T10:00:00.000Z');
    expiryFor(SubscriptionPlan.YEARLY, from);
    expect(from.toISOString()).toBe('2026-01-15T10:00:00.000Z');
  });
});
