import { ConfigService } from '@nestjs/config';
import { TypeOrmModuleOptions } from '@nestjs/typeorm';

export const databaseConfig = (
  configService: ConfigService,
): TypeOrmModuleOptions => ({
  type: 'postgres',
  host: configService.get<string>('database.host'),
  port: configService.get<number>('database.port'),
  username: configService.get<string>('database.username'),
  password: configService.get<string>('database.password'),
  database: configService.get<string>('database.database'),
  entities: [__dirname + '/../database/entities/*.entity{.ts,.js}'],
  // OFF everywhere. The schema is owned by backend/migrations/*.sql, and the entities
  // do not describe it exactly (column types, the shared subscription_tier enum). With
  // synchronize on, TypeORM tries to reconcile the difference with destructive DDL: it
  // was observed renaming subscription_tier and attempting to DROP it, which fails
  // because subscriptions.tier also uses it — and on a database where it did not fail,
  // it would have rewritten columns instead.
  synchronize: false,
  logging: configService.get<string>('nodeEnv') === 'development',
  // rejectUnauthorized stays false because managed providers hand out certificates
  // signed by their own roots, which are not in the image's trust store. It encrypts
  // without authenticating the server, so it is not a substitute for a private network.
  ssl: configService.get<boolean>('database.ssl')
    ? { rejectUnauthorized: false }
    : false,
});

