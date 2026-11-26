package turn

import (
	"strings"
	"testing"
	"time"
)

// The secret and vector below were NOT produced by this package. They come from a
// Python HMAC written straight from coturn's documented scheme, so a subtly wrong
// construction here — hex instead of base64, SHA-256 instead of SHA-1, the wrong
// separator, hashing the password instead of the username — fails rather than
// agreeing with itself.
const (
	testSecret     = "ongero-turn-test-secret"
	testUserID     = "11111111-2222-3333-4444-555555555555"
	wantUsername   = "1800000000:11111111-2222-3333-4444-555555555555"
	wantCredential = "MUDBi9THQCXWEwsXefDglfM6Mps="
)

// at returns the instant that makes Mint produce the fixed vector with a 1h TTL.
func at() time.Time { return time.Unix(1800000000, 0).Add(-time.Hour) }

func TestMintMatchesAnIndependentImplementation(t *testing.T) {
	got := Mint(testSecret, testUserID, time.Hour, at())

	if got.Username != wantUsername {
		t.Errorf("username = %q, want %q", got.Username, wantUsername)
	}
	if got.Credential != wantCredential {
		t.Errorf("credential = %q, want %q", got.Credential, wantCredential)
	}
	if got.ExpiresAt != 1800000000 {
		t.Errorf("expiresAt = %d, want 1800000000", got.ExpiresAt)
	}
}

func TestUsernameCarriesTheExpiryCoturnChecks(t *testing.T) {
	// coturn parses everything before the first colon as a unix timestamp and refuses
	// the credential once it has passed. If the TTL were not added, or were added in
	// milliseconds, every credential would be born expired or effectively immortal.
	now := time.Unix(1_700_000_000, 0)
	got := Mint(testSecret, "u", 90*time.Minute, now)

	if got.ExpiresAt != now.Add(90*time.Minute).Unix() {
		t.Errorf("expiresAt = %d, want %d", got.ExpiresAt, now.Add(90*time.Minute).Unix())
	}
	if !strings.HasPrefix(got.Username, "1700005400:") {
		t.Errorf("username = %q, want it to start with the expiry", got.Username)
	}
}

func TestDifferentSecretsProduceDifferentCredentials(t *testing.T) {
	// The whole mechanism rests on this: coturn accepts the credential only because
	// it can recompute the same HMAC from the same secret.
	a := Mint("secret-a", testUserID, time.Hour, at())
	b := Mint("secret-b", testUserID, time.Hour, at())

	if a.Username != b.Username {
		t.Fatalf("usernames should not depend on the secret: %q vs %q", a.Username, b.Username)
	}
	if a.Credential == b.Credential {
		t.Error("two different secrets produced the same credential")
	}
}

func TestEachUserGetsADistinctCredential(t *testing.T) {
	// So an allocation in coturn's log can be traced back to an account.
	a := Mint(testSecret, "user-a", time.Hour, at())
	b := Mint(testSecret, "user-b", time.Hour, at())

	if a.Username == b.Username || a.Credential == b.Credential {
		t.Error("two users were given the same credential")
	}
}

func TestCredentialIsBase64OfTwentyBytes(t *testing.T) {
	// SHA-1 is 20 bytes, which is 28 base64 characters with one '=' of padding.
	// coturn computes base64(HMAC-SHA1(...)); a hex digest would be 40 characters and
	// would be rejected, quietly, as simply the wrong password.
	got := Mint(testSecret, testUserID, time.Hour, at())
	if len(got.Credential) != 28 || !strings.HasSuffix(got.Credential, "=") {
		t.Errorf("credential = %q (len %d), want 28 base64 characters", got.Credential, len(got.Credential))
	}
}
