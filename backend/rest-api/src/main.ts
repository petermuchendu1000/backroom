import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { AppModule } from './app.module';

async function bootstrap() {
  // rawBody gives controllers req.rawBody, the bytes exactly as received. The
  // PalPluss webhook signature is an HMAC over those bytes: re-serialising the
  // parsed JSON can change key order, spacing or number formatting, and any of
  // those changes the hash. Without this the signature check cannot run at all.
  const app = await NestFactory.create(AppModule, { rawBody: true });

  // Global prefix
  app.setGlobalPrefix('api/v1');

  // CORS
  app.enableCors({
    origin: process.env.CORS_ORIGIN || '*',
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
    credentials: true,
  });

  // Validation
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: {
        enableImplicitConversion: true,
      },
    }),
  );

  // Swagger API documentation
  const config = new DocumentBuilder()
    .setTitle('Ongero API')
    .setDescription('Anonymous voice support platform API')
    .setVersion('1.0')
    .addBearerAuth()
    .addTag('auth', 'Authentication endpoints')
    .addTag('users', 'User profile and boundaries')
    .addTag('shares', 'Share requests')
    .addTag('calls', 'Call lifecycle')
    .addTag('feedback', 'Post-call feedback')
    .addTag('reports', 'User reports')
    .addTag('subscriptions', 'Subscription management')
    .addTag('payments', 'Payments and gateway webhooks')
    .addTag('mpesa', 'Direct Safaricom Daraja integration (legacy routes)')
    .addTag('health', 'Health checks')
    .build();

  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('docs', app, document);

  const port = process.env.PORT || 8080;
  await app.listen(port);

  console.log(`🚀 Ongero API running on http://localhost:${port}`);
  console.log(`📚 API Docs available at http://localhost:${port}/docs`);
}

bootstrap();

