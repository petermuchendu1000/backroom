// Command turn-credential mints a short-lived TURN credential and prints it.
//
// For two jobs. Operationally, it is how you check whether coturn is accepting what
// this service issues without waiting for a call to fail:
//
//	go run ./cmd/turn-credential -secret "$TURN_STATIC_AUTH_SECRET" -user debug
//	turnutils_uclient -t -u "<username>" -w "<credential>" turn.ongero.com
//
// And it is what backend/coturn/test/turn-auth.check.sh uses, so that check exercises
// the SAME code path the hub uses rather than a re-implementation of it in the test.
// A test that mints its own credentials proves only that the test can do HMAC.
package main

import (
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/ongero/ws-signaling/internal/turn"
)

func main() {
	secret := flag.String("secret", os.Getenv("TURN_STATIC_AUTH_SECRET"),
		"coturn's static-auth-secret; defaults to $TURN_STATIC_AUTH_SECRET")
	user := flag.String("user", "debug", "account id to embed in the username")
	ttl := flag.Duration("ttl", time.Hour, "how long the credential stays valid")
	format := flag.String("format", "text", "text or shell")
	flag.Parse()

	if *secret == "" {
		fmt.Fprintln(os.Stderr, "no secret: pass -secret or set TURN_STATIC_AUTH_SECRET")
		os.Exit(2)
	}

	creds := turn.Mint(*secret, *user, *ttl, time.Now())

	switch *format {
	case "shell":
		// Two lines, username then credential, so a script can read them without a
		// JSON parser.
		fmt.Println(creds.Username)
		fmt.Println(creds.Credential)
	default:
		fmt.Printf("username:   %s\n", creds.Username)
		fmt.Printf("credential: %s\n", creds.Credential)
		fmt.Printf("expires:    %s\n", time.Unix(creds.ExpiresAt, 0).UTC().Format(time.RFC3339))
	}
}
