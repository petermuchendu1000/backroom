import { IsIn, IsNotEmpty, IsString } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class InitiatePaymentDto {
  @ApiProperty({
    description: 'Phone number (Safaricom) in format 07XXXXXXXX or 254XXXXXXXXX',
    example: '0712345678',
  })
  @IsString()
  @IsNotEmpty()
  phoneNumber: string;

  @ApiProperty({
    description: 'Subscription plan',
    example: 'monthly',
    enum: ['monthly', 'yearly'],
  })
  // Without this, any string reached the controller and anything that was not
  // 'yearly' was silently charged the monthly price, then stored as the plan.
  @IsString()
  @IsNotEmpty()
  @IsIn(['monthly', 'yearly'])
  plan: 'monthly' | 'yearly';
}

export class QueryPaymentDto {
  @ApiProperty({
    description: 'Checkout Request ID from STK Push response',
    example: 'ws_CO_01012026123456789',
  })
  @IsString()
  @IsNotEmpty()
  checkoutRequestId: string;
}

