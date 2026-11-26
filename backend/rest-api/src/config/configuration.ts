export default () => ({
  // App
  port: parseInt(process.env.PORT ?? '8080', 10),
  nodeEnv: process.env.NODE_ENV || 'development',

  // Database
  database: {
    host: process.env.POSTGRES_HOST || 'localhost',
    port: parseInt(process.env.POSTGRES_PORT ?? '5432', 10),
    username: process.env.POSTGRES_USER || 'backroom',
    password: process.env.POSTGRES_PASSWORD || 'backroom_dev_password',
    database: process.env.POSTGRES_DB || 'backroom',
  },

  // Redis
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT ?? '6379', 10),
    url: process.env.REDIS_URL || 'redis://localhost:6379',
  },

  // JWT
  jwt: {
    // MUST match the signalling service's default (ws-signaling cmd/server/main.go)
    // and both docker-compose services. The REST API mints the token; the Go
    // signalling service verifies it. Three different defaults used to live in
    // this repo, so `docker compose up` with no .env produced tokens that every
    // WebSocket upgrade rejected with 401.
    // .github/scripts/assert_shared_jwt_default.py keeps them in step.
    secret: process.env.JWT_SECRET || 'dev_jwt_secret_change_me_in_production',
    expiresIn: process.env.JWT_EXPIRES_IN || '15m',
    refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '7d',
  },

  // TURN/STUN
  turn: {
    url: process.env.TURN_URL || 'turn:localhost:3478',
    stunUrl: process.env.STUN_URL || 'stun:localhost:3478',
    // coturn's static-auth-secret. The SAME value must reach coturn itself and the
    // signalling service — three components computing one HMAC. No default: a
    // committed TURN secret is an open relay, so unset means no TURN is offered.
    // .github/scripts/assert_shared_turn_secret.py keeps the three in step.
    secret: process.env.TURN_STATIC_AUTH_SECRET || '',
    ttl: parseInt(process.env.TURN_CREDENTIAL_TTL ?? '3600', 10),
  },

  // M-Pesa (Safaricom Daraja API)
  mpesa: {
    environment: process.env.MPESA_ENVIRONMENT || 'sandbox', // 'sandbox' or 'production'
    consumerKey: process.env.MPESA_CONSUMER_KEY || '',
    consumerSecret: process.env.MPESA_CONSUMER_SECRET || '',
    shortcode: process.env.MPESA_SHORTCODE || '', // Paybill or Till number
    passkey: process.env.MPESA_PASSKEY || '', // Lipa Na M-Pesa passkey
    callbackUrl: process.env.MPESA_CALLBACK_URL || 'https://your-domain.com/api/v1/mpesa/callback',
    timeoutUrl: process.env.MPESA_TIMEOUT_URL || 'https://your-domain.com/api/v1/mpesa/timeout',
    resultUrl: process.env.MPESA_RESULT_URL || 'https://your-domain.com/api/v1/mpesa/result',
  },

  // Which gateway new payments go through. See PaymentProvider:
  // 'palpluss' (default) or 'daraja' to talk to Safaricom directly.
  payments: {
    provider: process.env.PAYMENTS_PROVIDER || 'palpluss',
  },

  // PalPluss (https://docs.palpluss.com) — an aggregator fronting Daraja.
  palpluss: {
    baseUrl: process.env.PALPLUSS_BASE_URL || 'https://api.palpluss.com/v1',
    // pp_live_… or pp_test_…, sent as HTTP Basic with an empty password. Never commit
    // it. Test keys hit the same URL and move simulated money.
    apiKey: process.env.PALPLUSS_API_KEY || '',
    callbackUrl:
      process.env.PALPLUSS_CALLBACK_URL ||
      'https://api.ongero.com/api/v1/payments/palpluss/webhook',
    // Optional: routes the prompt through one specific shortcode. The account default
    // is used when this is unset.
    channelId: process.env.PALPLUSS_CHANNEL_ID || '',
    // whsec_… from the PalPluss console, under Webhooks, after adding the callback
    // domain. When unset, callbacks are accepted WITHOUT a signature check — PalPluss
    // still delivers to unregistered domains, just unsigned.
    webhookSecret: process.env.PALPLUSS_WEBHOOK_SECRET || '',
  },

  // FCM (Firebase Cloud Messaging)
  fcm: {
    serviceAccountPath: process.env.GOOGLE_APPLICATION_CREDENTIALS || './firebase-service-account.json',
    projectId: process.env.FCM_PROJECT_ID || 'backroom-b51b1',
  },

  // Subscription pricing (in KES)
  subscription: {
    monthlyPrice: parseInt(process.env.SUBSCRIPTION_MONTHLY_PRICE ?? '500', 10),
    yearlyPrice: parseInt(process.env.SUBSCRIPTION_YEARLY_PRICE ?? '5000', 10),
    trialDays: parseInt(process.env.SUBSCRIPTION_TRIAL_DAYS ?? '7', 10),
  },
});

