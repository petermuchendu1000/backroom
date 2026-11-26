package handlers

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/ongero/ws-signaling/internal/auth"
)

const testSecret = "test_secret_for_unit_tests"

// mint builds a JWT signed with the given secret.
func mint(t *testing.T, secret, sub string, exp time.Time) string {
	t.Helper()
	claims := auth.Claims{
		Sub:              sub,
		Anonymous:        false,
		SubscriptionTier: "free",
		RegisteredClaims: jwt.RegisteredClaims{ExpiresAt: jwt.NewNumericDate(exp)},
	}
	s, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte(secret))
	if err != nil {
		t.Fatalf("minting token: %v", err)
	}
	return s
}

// serve drives Handle with a nil hub. The hub is only dereferenced AFTER the
// auth decision (websocket.go: RegisterClient call), so a nil hub is safe for
// every case that must be rejected. httptest.NewRecorder does not implement
// http.Hijacker, so an ACCEPTED request fails at the upgrade step instead of
// reaching the hub -- which is exactly what lets us assert "not rejected"
// without a Redis-backed hub.
func serve(t *testing.T, target string, header map[string]string) int {
	t.Helper()
	h := NewWebSocketHandler(nil, auth.NewJWTValidator(testSecret))
	req := httptest.NewRequest(http.MethodGet, target, nil)
	req.Header.Set("Connection", "Upgrade")
	req.Header.Set("Upgrade", "websocket")
	req.Header.Set("Sec-WebSocket-Version", "13")
	req.Header.Set("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
	for k, v := range header {
		req.Header.Set(k, v)
	}
	rec := httptest.NewRecorder()
	h.Handle(rec, req)
	return rec.Code
}

// TestHandle_RejectsUnauthenticated is the security contract: a client that
// cannot present a cryptographically valid, non-empty JWT subject MUST NOT be
// admitted. Each case must yield 401.
func TestHandle_RejectsUnauthenticated(t *testing.T) {
	past := time.Now().Add(-1 * time.Hour)
	future := time.Now().Add(1 * time.Hour)

	cases := []struct {
		name   string
		target string
		header map[string]string
	}{
		{"no token at all", "/ws", nil},
		{"malformed token in query", "/ws?token=not-a-jwt-at-all", nil},
		{"empty Authorization Bearer", "/ws", map[string]string{"Authorization": "Bearer "}},
		{"token signed with wrong secret", "/ws?token=" + mint(t, "an-attackers-secret", "victim-id", future), nil},
		{"expired token, correct secret", "/ws?token=" + mint(t, testSecret, "user-1", past), nil},
		{"valid signature but EMPTY sub", "/ws?token=" + mint(t, testSecret, "", future), nil},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := serve(t, tc.target, tc.header)
			if got != http.StatusUnauthorized {
				t.Errorf("admitted an unauthenticated client: got status %d, want %d (%s)",
					got, http.StatusUnauthorized, tc.name)
			}
		})
	}
}

// TestHandle_AcceptsValidToken guards against over-rejection: a properly signed,
// unexpired token with a non-empty subject must get PAST the auth check.
// It cannot reach 101 here because the recorder is not a Hijacker, so the only
// assertion that matters is that it is NOT rejected with 401.
func TestHandle_AcceptsValidToken(t *testing.T) {
	tok := mint(t, testSecret, "legit-user-42", time.Now().Add(1*time.Hour))

	t.Run("via query parameter", func(t *testing.T) {
		if got := serve(t, "/ws?token="+tok, nil); got == http.StatusUnauthorized {
			t.Errorf("rejected a VALID token: got 401, want anything else (observed upgrade-stage status)")
		}
	})

	t.Run("via Authorization header", func(t *testing.T) {
		if got := serve(t, "/ws", map[string]string{"Authorization": "Bearer " + tok}); got == http.StatusUnauthorized {
			t.Errorf("rejected a VALID token via header: got 401, want anything else")
		}
	})
}
