#!/usr/bin/env bash
#
# Does coturn actually accept what we mint?
#
# Runs the REAL turnserver.conf against a real coturn, mints credentials with the REAL
# production code (cmd/turn-credential, which calls internal/turn.Mint — the same
# function the hub calls), and attempts a real relay allocation with turnutils_uclient.
#
# Unit tests on either side cannot answer this. They prove our HMAC matches a vector;
# they say nothing about whether coturn's config puts it in the mode where that HMAC
# is what gets checked. The failure this guards against — a credential that is
# perfectly well-formed and simply not accepted — produced no error anywhere except a
# call that would not connect.
#
# What it pins:
#   - a freshly minted credential      -> allocation succeeds, data relays
#   - the old static username/password -> refused
#   - a credential under another secret-> refused
#   - an already-expired credential    -> refused
#
# Prerequisites: coturn (turnserver, turnutils_uclient) and go on PATH.
# Not part of CI: it needs coturn installed and binds UDP ports.
#
#   backend/coturn/test/turn-auth.check.sh
#
set -o pipefail
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CONF="$ROOT/backend/coturn/turnserver.conf"
PORT="${TURN_TEST_PORT:-3480}"
MIN_RELAY="${TURN_TEST_MIN_RELAY:-50000}"
MAX_RELAY="${TURN_TEST_MAX_RELAY:-50100}"

# Only ever used by this script, against a loopback-only server.
SECRET="turn_auth_check_secret_$$"

failures=0
check() {
  if [ "$2" = "$3" ]; then
    printf 'PASS  %s\n' "$1"
  else
    printf 'FAIL  %s\n      expected: %s\n      actual:   %s\n' "$1" "$2" "$3"
    failures=$((failures + 1))
  fi
}

for tool in turnserver turnutils_uclient go; do
  command -v "$tool" > /dev/null 2>&1 || { echo "MISSING: $tool. See the header."; exit 1; }
done

# Same lesson as the signalling check: a server left behind by an earlier run would
# answer for this one, and every assertion would pass without testing anything.
if (exec 3<>/dev/udp/127.0.0.1/"$PORT") 2>/dev/null; then
  exec 3<&- 2>/dev/null
fi
if pgrep -f "turnserver .*$PORT" > /dev/null 2>&1; then
  echo "A turnserver is already running on $PORT. Stop it first: pkill -f turnserver"
  exit 1
fi

cleanup() { [ -n "${TURN_PID:-}" ] && kill "$TURN_PID" 2>/dev/null; wait 2>/dev/null; }
trap cleanup EXIT

# The real config, with only the things that cannot work in a sandbox overridden:
# loopback binding, unprivileged ports, a small relay range, and the secret, which
# the committed file deliberately does not contain.
turnserver -c "$CONF" \
  --static-auth-secret="$SECRET" \
  --listening-port="$PORT" \
  --listening-ip=127.0.0.1 \
  --relay-ip=127.0.0.1 \
  --min-port="$MIN_RELAY" --max-port="$MAX_RELAY" \
  --allow-loopback-peers --cli-password=turnauthcheck \
  --no-tls --no-dtls \
  --log-file=stdout --simple-log \
  > /tmp/ongero-turnserver.log 2>&1 &
TURN_PID=$!
sleep 3

if ! kill -0 "$TURN_PID" 2>/dev/null; then
  echo "turnserver did not start:"; tail -20 /tmp/ongero-turnserver.log; exit 1
fi

# Prove the committed config is the one in force before asserting anything about it.
#
# Without this the check cannot tell a passing config from no config: the command line
# supplies ports, the secret and the loopback allowance, which between them are enough
# to relay a call even if turnserver.conf were never read. The committed file is what
# ships, so what it says has to be what is being tested. The realm is the tell — coturn
# has no default for it, so "ongero.com" can only have come from our file.
#
# --log-file=stdout overrides the no-stdout-log the config sets for production.
# Two markers, both of which can only have come from the file. coturn prints its
# "Config file found" line before --log-file takes effect, so that one is not
# available here; the realm and the blacklist are.
if ! grep -q "Default realm: ongero.com" /tmp/ongero-turnserver.log; then
  echo "coturn is not using the realm from turnserver.conf — it has no default realm,"
  echo "so the committed config is not in force and this check would prove nothing."
  exit 1
fi
if ! grep -q "Black listing: 100.64.0.0-100.127.255.255" /tmp/ongero-turnserver.log; then
  echo "the denied-peer-ip ranges from turnserver.conf are not loaded"
  exit 1
fi
echo "1. coturn up on $PORT, confirmed running the committed turnserver.conf (realm ongero.com)"
echo

# turnutils_uclient reports plenty; what matters is whether the allocation happened.
attempt() { # username password -> "ALLOWED" | "REFUSED"
  local out
  out="$(timeout 20 turnutils_uclient -t -u "$1" -w "$2" -p "$PORT" -n 1 -y 127.0.0.1 2>&1)"
  if printf '%s' "$out" | grep -qiE '401|unauthor|cannot complete allocation'; then
    printf 'REFUSED'
  elif printf '%s' "$out" | grep -qiE 'total transmit time|success|channel bind'; then
    printf 'ALLOWED'
  else
    printf 'unclear: %s' "$(printf '%s' "$out" | tail -1 | cut -c1-60)"
  fi
}

mint() { # user ttl -> prints username then credential
  (cd "$ROOT/backend/ws-signaling" && go run ./cmd/turn-credential \
    -secret "$SECRET" -user "$1" -ttl "$2" -format shell)
}

# ---- the credential the hub now issues ---------------------------------------------
creds="$(mint call-peer-1 1h)" || { echo "minting failed"; exit 1; }
USERNAME="$(printf '%s' "$creds" | sed -n 1p)"
CREDENTIAL="$(printf '%s' "$creds" | sed -n 2p)"
echo "   minted by internal/turn.Mint: $USERNAME"
check "2. a freshly minted credential relays" "ALLOWED" "$(attempt "$USERNAME" "$CREDENTIAL")"

# ---- what the hub used to issue ------------------------------------------------------
check "3. the old static username/password is refused" "REFUSED" \
  "$(attempt backroom backroom_turn_secret)"

# ---- a credential from someone else's secret -----------------------------------------
OTHER="$( (cd "$ROOT/backend/ws-signaling" && go run ./cmd/turn-credential \
  -secret "a-secret-we-do-not-share" -user attacker -ttl 1h -format shell) )"
check "4. a credential minted under another secret is refused" "REFUSED" \
  "$(attempt "$(printf '%s' "$OTHER" | sed -n 1p)" "$(printf '%s' "$OTHER" | sed -n 2p)")"

# ---- an expired one --------------------------------------------------------------------
EXPIRED="$(mint stale -1h)"
check "5. an expired credential is refused" "REFUSED" \
  "$(attempt "$(printf '%s' "$EXPIRED" | sed -n 1p)" "$(printf '%s' "$EXPIRED" | sed -n 2p)")"

echo
if [ "$failures" -eq 0 ]; then echo "PASS: 0 failed check(s)"; else echo "FAIL: $failures failed check(s)"; fi
exit $([ "$failures" -eq 0 ] && echo 0 || echo 1)
