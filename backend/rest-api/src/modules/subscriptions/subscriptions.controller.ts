import { Controller, Get, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { SubscriptionsService } from './subscriptions.service';

@ApiTags('subscriptions')
@Controller('subscriptions')
export class SubscriptionsController {
  constructor(private readonly subscriptions: SubscriptionsService) {}

  /**
   * What this person is entitled to right now.
   *
   * Derived from the subscriptions table rather than read off users.subscription_tier,
   * so it is correct the moment a period ends rather than whenever the sweep last ran.
   */
  @Get('me')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Current subscription entitlement' })
  @ApiResponse({ status: 200, description: 'Entitlement returned' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async me(@Req() req: any) {
    const { tier, expiresAt } = await this.subscriptions.current(req.user.id);
    return { tier, expiresAt, isPlus: tier !== 'free' };
  }
}
