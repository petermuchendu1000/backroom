package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/ongero/ws-signaling/internal/auth"
	"github.com/ongero/ws-signaling/internal/fcm"
	"github.com/ongero/ws-signaling/internal/handlers"
	"github.com/ongero/ws-signaling/internal/hub"
	"github.com/ongero/ws-signaling/internal/matching"
	redisClient "github.com/ongero/ws-signaling/internal/redis"
)

func main() {
	log.Println("🚀 Starting Ongero WebSocket Signaling Server...")

	// Load configuration
	config := loadConfig()

	// Initialize Redis
	rdb := redisClient.NewClient(config.RedisURL)
	defer rdb.Close()

	// Test Redis connection
	ctx := context.Background()
	if err := rdb.Ping(ctx).Err(); err != nil {
		log.Fatalf("❌ Failed to connect to Redis: %v", err)
	}
	log.Println("✅ Connected to Redis")

	// Initialize JWT validator
	jwtValidator := auth.NewJWTValidator(config.JWTSecret)

	// Initialize Hub (manages all WebSocket connections)
	h := hub.NewHub(rdb, config.TurnURL, config.TurnSecret, config.TurnTTL)
	go h.Run()

	// Initialize FCM client for push notifications
	fcmClient, err := fcm.NewFCMClient()
	if err != nil {
		log.Printf("⚠️ Failed to initialize FCM client: %v (push notifications will be disabled)", err)
	} else {
		log.Println("✅ FCM client initialized for push notifications")
	}

	// Initialize Matching Worker
	matcher := matching.NewMatcher(rdb, h, fcmClient)
	go matcher.Run(ctx)

	// Setup HTTP routes
	mux := http.NewServeMux()

	// Health check
	mux.HandleFunc("/health", handlers.HealthHandler)

	// WebSocket endpoint
	wsHandler := handlers.NewWebSocketHandler(h, jwtValidator)
	mux.HandleFunc("/ws", wsHandler.Handle)

	// Create server
	server := &http.Server{
		Addr:         ":" + config.Port,
		Handler:      mux,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Start server in goroutine
	go func() {
		log.Printf("🌐 WebSocket server listening on port %s", config.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("❌ Server error: %v", err)
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("🛑 Shutting down server...")

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Fatalf("❌ Server forced to shutdown: %v", err)
	}

	log.Println("👋 Server stopped")
}

// Config holds server configuration
type Config struct {
	Port       string
	RedisURL   string
	JWTSecret  string
	TurnURL    string
	TurnSecret string
	TurnTTL    time.Duration
}

func loadConfig() *Config {
	return &Config{
		Port:     getEnv("WS_PORT", "8443"),
		RedisURL: getEnv("REDIS_URL", "redis://localhost:6379"),
		// MUST match the REST API's default (src/config/configuration.ts) and both
		// docker-compose services: that service mints the tokens this one verifies.
		JWTSecret: getEnv("JWT_SECRET", "dev_jwt_secret_change_me_in_production"),
		TurnURL:   getEnv("TURN_URL", "turn:192.168.1.160:3478"),
		// Deliberately NO default. This is coturn's static-auth-secret: the same
		// value must be given to coturn, to this service and to the REST API, and a
		// default committed here would be a shared secret published in a public
		// repository — which is to say an open relay for anyone who reads it. Unset,
		// the hub offers STUN only and says so.
		TurnSecret: getEnv("TURN_STATIC_AUTH_SECRET", ""),
		TurnTTL:    time.Duration(getEnvInt("TURN_CREDENTIAL_TTL", 3600)) * time.Second,
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// getEnvInt reads an integer setting, falling back when it is unset or unreadable.
// A malformed TTL should not stop the server from starting; it should run with the
// documented default and say so.
func getEnvInt(key string, defaultValue int) int {
	raw := os.Getenv(key)
	if raw == "" {
		return defaultValue
	}
	parsed, err := strconv.Atoi(raw)
	if err != nil || parsed <= 0 {
		log.Printf("⚠️ %s=%q is not a positive integer; using %d", key, raw, defaultValue)
		return defaultValue
	}
	return parsed
}
