import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { SubscriptionTier, User } from '../../database/entities/user.entity';
import { SubscriptionsService } from '../subscriptions/subscriptions.service';
import { AuthService, JwtPayload } from './auth.service';

/**
 * The token's subscriptionTier claim is what the signalling service enforces call
 * limits from, and that service cannot reach the database. So the claim must come from
 * the derived entitlement, never from the cached users.subscription_tier column — a
 * token minted from a stale column keeps granting Plus for its whole lifetime, and
 * refreshing renews it indefinitely.
 *
 * Every test below sets the cached column to the OPPOSITE of the truth, so anything
 * that reads the column instead of deriving fails loudly.
 */
describe('AuthService — subscription tier in the token', () => {
  let service: AuthService;
  let currentTier: jest.Mock;
  let signed: JwtPayload[];
  let user: Partial<User>;

  beforeEach(async () => {
    currentTier = jest.fn();
    signed = [];
    user = {
      id: 'user-1',
      anonymous: true,
      deviceId: 'device-1',
      createdAt: new Date('2026-01-01T00:00:00.000Z'),
      subscriptionTier: SubscriptionTier.PLUS, // the stale cache, deliberately wrong
    };

    const moduleRef = await Test.createTestingModule({
      providers: [
        AuthService,
        {
          provide: getRepositoryToken(User),
          useValue: {
            findOne: jest.fn(async () => user),
            create: jest.fn((x) => x),
            save: jest.fn(async (x) => x),
          },
        },
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn((payload: JwtPayload) => {
              signed.push(payload);
              return 'token';
            }),
          },
        },
        { provide: ConfigService, useValue: { get: jest.fn(() => '15m') } },
        { provide: SubscriptionsService, useValue: { currentTier } },
      ],
    }).compile();

    service = moduleRef.get(AuthService);
  });

  it('mints a free token when the period has lapsed, despite a cached plus', async () => {
    currentTier.mockResolvedValue(SubscriptionTier.FREE);

    const result = await service.authenticateAnonymous({ deviceId: 'device-1' } as never);

    expect(signed[0].subscriptionTier).toBe(SubscriptionTier.FREE);
    expect(result.user.subscriptionTier).toBe(SubscriptionTier.FREE);
    expect(currentTier).toHaveBeenCalledWith('user-1');
  });

  it('mints a plus token when a live subscription exists but the cache says free', async () => {
    user.subscriptionTier = SubscriptionTier.FREE;
    currentTier.mockResolvedValue(SubscriptionTier.PLUS);

    const result = await service.authenticateAnonymous({ deviceId: 'device-1' } as never);

    expect(signed[0].subscriptionTier).toBe(SubscriptionTier.PLUS);
    expect(result.user.subscriptionTier).toBe(SubscriptionTier.PLUS);
  });

  it('does not renew plus on refresh after a lapse', async () => {
    currentTier.mockResolvedValue(SubscriptionTier.FREE);

    await service.refreshToken('user-1');

    expect(signed[0].subscriptionTier).toBe(SubscriptionTier.FREE);
  });

  it('refresh reflects a subscription bought since the last token', async () => {
    user.subscriptionTier = SubscriptionTier.FREE;
    currentTier.mockResolvedValue(SubscriptionTier.PLUS);

    await service.refreshToken('user-1');

    expect(signed[0].subscriptionTier).toBe(SubscriptionTier.PLUS);
  });

  it('returns null on refresh for a user that no longer exists', async () => {
    const repo = service as unknown as { userRepository: { findOne: jest.Mock } };
    repo.userRepository.findOne = jest.fn(async () => null);

    expect(await service.refreshToken('gone')).toBeNull();
  });
});
