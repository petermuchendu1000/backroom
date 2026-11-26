// Package turn mints the short-lived credentials coturn accepts.
//
// coturn is configured with use-auth-secret, its "TURN REST API" mode. In that mode a
// username is "<unix-expiry>:<anything>" and the password is the base64 HMAC-SHA1 of
// that username under a secret shared with the server. coturn recomputes the HMAC and
// compares, so no credential is ever stored and every one expires on its own.
//
// This replaces a single static username and password that was handed to every client
// on every match. That credential never expired, was identical for everyone, and was
// sent over the wire to anyone who connected — so one person reading one message had
// permanent free use of the relay. It also did not work: with use-auth-secret enabled
// coturn ignores static user entries, so every relayed call was failing anyway.
//
// The algorithm here must stay identical to the REST API's generateTurnCredentials
// (backend/rest-api/src/modules/auth/auth.service.ts). Two implementations of one
// wire format is a liability, so turn_parity.check.sh compares them.
package turn

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"fmt"
	"time"
)

// Credentials are what a client presents to coturn.
type Credentials struct {
	Username   string
	Credential string
	// ExpiresAt is when coturn will stop accepting these, as unix seconds. It is
	// already encoded in Username; it is returned separately so callers do not have
	// to parse their own output to find out.
	ExpiresAt int64
}

// Mint builds a credential valid for ttl from now.
//
// userID only has to make the username unique enough to read in a server log —
// coturn does not look it up anywhere. It is included so an allocation can be traced
// back to an account when someone abuses the relay.
func Mint(secret, userID string, ttl time.Duration, now time.Time) Credentials {
	expiry := now.Add(ttl).Unix()
	username := fmt.Sprintf("%d:%s", expiry, userID)

	mac := hmac.New(sha1.New, []byte(secret))
	// Hash.Write never returns an error, per the hash.Hash contract.
	mac.Write([]byte(username))

	return Credentials{
		Username:   username,
		Credential: base64.StdEncoding.EncodeToString(mac.Sum(nil)),
		ExpiresAt:  expiry,
	}
}
