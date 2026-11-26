import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { Subscription } from '../../database/entities/subscription.entity';
import { SubscriptionTier } from '../../database/entities/user.entity';
import { SubscriptionsService } from './subscriptions.service';

/**
 * These run without a database, so they check the logic and — above all — the shape of
 * what TypeORM hands back.
 *
 * `manager.query()` returns [rows, affectedCount] for an UPDATE ... RETURNING. Reading
 * `.length` off that tuple yields 2 no matter what happened, which is exactly the bug
 * that shipped the first time: every sweep reported two lapsed and two downgraded, and
 * no user was actually downgraded because `row.user_id` was undefined. The counting
 * tests below fail if anyone goes back to reading the tuple directly.
 *
 * The end-to-end behaviour against a real PostgreSQL lives in
 * test/subscriptions.db-check.ts, which needs a database and so is not part of CI.
 */
describe('SubscriptionsService', () => {
  let service: SubscriptionsService;
  let getOne: jest.Mock;
  let query: jest.Mock;

  beforeEach(async () => {
    getOne = jest.fn();
    query = jest.fn();

    const qb = {
      where: jest.fn().mockReturnThis(),
      andWhere: jest.fn().mockReturnThis(),
      orderBy: jest.fn().mockReturnThis(),
      getOne,
    };

    const moduleRef = await Test.createTestingModule({
      providers: [
        SubscriptionsService,
        {
          provide: getRepositoryToken(Subscription),
          useValue: { createQueryBuilder: () => qb },
        },
        {
          provide: DataSource,
          useValue: {
            transaction: (cb: (m: unknown) => Promise<unknown>) => cb({ query }),
          },
        },
      ],
    }).compile();

    service = moduleRef.get(SubscriptionsService);
  });

  describe('currentTier', () => {
    it('is free when nothing is active', async () => {
      getOne.mockResolvedValue(null);
      expect(await service.currentTier('u1')).toBe(SubscriptionTier.FREE);
    });

    it('is the tier of the live subscription', async () => {
      getOne.mockResolvedValue({ tier: SubscriptionTier.PLUS });
      expect(await service.currentTier('u1')).toBe(SubscriptionTier.PLUS);
    });

    it('reports plus only when something is live', async () => {
      getOne.mockResolvedValue(null);
      expect(await service.isPlus('u1')).toBe(false);

      getOne.mockResolvedValue({ tier: SubscriptionTier.PLUS });
      expect(await service.isPlus('u1')).toBe(true);
    });
  });

  describe('expireLapsed', () => {
    it('counts rows, not the [rows, affected] tuple TypeORM returns', async () => {
      query
        .mockResolvedValueOnce([[{ user_id: 'a' }, { user_id: 'b' }, { user_id: 'c' }], 3])
        .mockResolvedValueOnce([[{ id: 'a' }], 1]);

      // Reading .length off the tuple would give 2 and 2 here.
      expect(await service.expireLapsed()).toEqual({ lapsed: 3, downgraded: 1 });
    });

    it('does nothing further when nothing has lapsed', async () => {
      query.mockResolvedValueOnce([[], 0]);

      expect(await service.expireLapsed()).toEqual({ lapsed: 0, downgraded: 0 });
      expect(query).toHaveBeenCalledTimes(1); // no pointless user update
    });

    it('deduplicates users holding several lapsed subscriptions', async () => {
      query
        .mockResolvedValueOnce([[{ user_id: 'a' }, { user_id: 'a' }], 2])
        .mockResolvedValueOnce([[{ id: 'a' }], 1]);

      await service.expireLapsed();

      const [, params] = query.mock.calls[1];
      expect(params[0]).toEqual(['a']);
    });

    it('downgrades to free and passes the same instant to both statements', async () => {
      const now = new Date('2026-06-01T12:00:00.000Z');
      query
        .mockResolvedValueOnce([[{ user_id: 'a' }], 1])
        .mockResolvedValueOnce([[{ id: 'a' }], 1]);

      await service.expireLapsed(now);

      expect(query.mock.calls[0][1]).toEqual([now]);
      const [, params] = query.mock.calls[1];
      expect(params[1]).toBe(SubscriptionTier.FREE);
      expect(params[2]).toBe(now);
    });

    it('reports zero downgrades when everyone still has something live', async () => {
      query
        .mockResolvedValueOnce([[{ user_id: 'a' }], 1])
        .mockResolvedValueOnce([[], 0]);

      expect(await service.expireLapsed()).toEqual({ lapsed: 1, downgraded: 0 });
    });
  });
});
