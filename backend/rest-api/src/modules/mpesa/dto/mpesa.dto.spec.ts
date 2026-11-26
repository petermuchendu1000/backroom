import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { InitiatePaymentDto } from './mpesa.dto';

/**
 * `plan` decides the price. It used to be @IsString() only, so anything that was not
 * the exact word 'yearly' fell through to the monthly price — a request for
 * plan: 'lifetime' was charged 500 and would now have been stored as the plan.
 */
describe('InitiatePaymentDto', () => {
  const build = (plan: unknown) =>
    plainToInstance(InitiatePaymentDto, { phoneNumber: '0712345678', plan });

  it.each(['monthly', 'yearly'])('accepts the real plan %s', (plan) => {
    expect(validateSync(build(plan))).toHaveLength(0);
  });

  it.each(['lifetime', 'free', 'Monthly', 'YEARLY', '', 'monthly '])(
    'rejects %p',
    (plan) => {
      const errors = validateSync(build(plan));
      expect(errors.length).toBeGreaterThan(0);
      expect(JSON.stringify(errors)).toContain('plan');
    },
  );

  it('rejects a missing plan', () => {
    const dto = plainToInstance(InitiatePaymentDto, { phoneNumber: '0712345678' });
    expect(validateSync(dto).length).toBeGreaterThan(0);
  });

  it('still requires a phone number', () => {
    const dto = plainToInstance(InitiatePaymentDto, { plan: 'monthly' });
    expect(validateSync(dto).length).toBeGreaterThan(0);
  });
});
