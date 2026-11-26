package handlers

import (
	"encoding/json"
	"log"
	"net/http"

	"github.com/gorilla/websocket"
	"github.com/ongero/ws-signaling/internal/auth"
	"github.com/ongero/ws-signaling/internal/hub"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		// In production, validate origin
		return true
	},
}

// WebSocketHandler handles WebSocket connections
type WebSocketHandler struct {
	hub          *hub.Hub
	jwtValidator *auth.JWTValidator
}

// NewWebSocketHandler creates a new WebSocket handler
func NewWebSocketHandler(h *hub.Hub, jwtValidator *auth.JWTValidator) *WebSocketHandler {
	return &WebSocketHandler{
		hub:          h,
		jwtValidator: jwtValidator,
	}
}

// Handle authenticates the caller, then upgrades HTTP to WebSocket and registers
// the client.
//
// Every client admitted to the hub MUST carry a cryptographically valid,
// unexpired JWT with a non-empty subject. Authentication failures are refused
// with 401 and are never downgraded to an anonymous identity: an anonymous
// identity here would let an unauthenticated caller enter the matching pool and
// be paired with a real user.
//
// The 401 body is deliberately generic; the specific reason is logged
// server-side only.
func (h *WebSocketHandler) Handle(w http.ResponseWriter, r *http.Request) {
	// Get token from query parameter or header
	token := r.URL.Query().Get("token")
	if token == "" {
		token = r.Header.Get("Authorization")
	}

	if token == "" {
		log.Printf("🚫 WebSocket rejected: no token presented")
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	claims, err := h.jwtValidator.ValidateToken(token)
	if err != nil {
		log.Printf("🚫 WebSocket rejected: invalid token: %v", err)
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	userID := claims.GetUserID()
	if userID == "" {
		log.Printf("🚫 WebSocket rejected: token carries no subject")
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	// Upgrade to WebSocket
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("❌ WebSocket upgrade failed: %v", err)
		return
	}

	// Register client with hub
	client := h.hub.RegisterClient(conn, userID)

	// Start read/write pumps
	go client.WritePump()
	go client.ReadPump()

	log.Printf("✅ WebSocket connected: %s", userID)
}

// HealthHandler returns server health status
func HealthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	response := map[string]interface{}{
		"status":    "ok",
		"service":   "ws-signaling",
		"timestamp": "",
	}

	json.NewEncoder(w).Encode(response)
}

