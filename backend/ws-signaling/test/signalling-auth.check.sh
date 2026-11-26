#!/usr/bin/env bash
#
# Can the app actually connect to the signalling server?
#
# Both services, for real: the NestJS API mints a token against real PostgreSQL, the
# Go signalling server verifies it against real Redis, and a WebSocket upgrade is
# attempted exactly the way the Android client now attempts it.
#
# This is the only way to check the thing that was broken. The two services agreeing
# requires a shared JWT secret, a client that sends a credential, and a server that
# accepts it — and each half passed its own tests while the whole refused every
# connection. Unit tests on either side cannot see that.
#
# What it pins:
#   - no token            -> 401, as websocket.go intends
#   - a forged token      -> 401
#   - an expired token    -> 401
#   - a real token        -> 101, and the greeting names the SERVER's user id
#   - that id is the same users.id the REST API reports for the same device
#
# Prerequisites: PostgreSQL with the three migrations applied, redis-server and go on
# PATH. Not part of CI: it needs both.
#
#   backend/ws-signaling/test/signalling-auth.check.sh
#
set -o pipefail
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROBE="$ROOT/backend/ws-signaling/test/ws-probe.py"
API_PORT="${API_PORT:-8096}"
WS_PORT="${WS_PORT:-8445}"
REDIS_PORT="${REDIS_PORT:-6390}"
API="http://127.0.0.1:$API_PORT/api/v1"

# One secret, both services. Passing it explicitly rather than relying on the shared
# default is the point: this check must fail if the CLIENT or the SERVER is wrong, not
# merely if someone edited a default. .github/scripts/assert_shared_jwt_default.py
# covers the defaults.
SECRET="signalling_check_shared_secret"

failures=0
check() {
  if [ "$2" = "$3" ]; then
    echo "PASS  $1"
  else
    echo "FAIL  $1"
    echo "      expected: $2"
    echo "      actual:   $3"
    failures=$((failures + 1))
  fi
}

cleanup() {
  for pid in ${API_PID:-} ${WS_PID:-} ${REDIS_PID:-}; do kill "$pid" 2>/dev/null; done
  wait 2>/dev/null
  # Give the kernel a moment to release the listeners, so a run immediately after
  # this one does not trip the in-use guard above.
  sleep 1
}
trap cleanup EXIT

# Refuse to run if anything already holds these ports.
#
# Learned the hard way: `go run` leaves the compiled binary alive when its parent is
# killed, so a previous run's server stayed bound and answered the next one. Three
# deliberately broken runs "passed" because they were talking to the correct server
# from the run before. A harness that can report a pass without exercising the code
# under test is worse than no harness, so this is fatal rather than a warning.
for port in "$API_PORT" "$WS_PORT" "$REDIS_PORT"; do
  if (exec 3<>/dev/tcp/127.0.0.1/"$port") 2>/dev/null; then
    exec 3<&- 2>/dev/null
    echo "Port $port is already in use. A previous run may have left a server behind:"
    echo "  pkill -f 'exe/server'; pkill -f 'ts-node -T src/main.ts'"
    exit 1
  fi
done

PSQL="psql -h ${POSTGRES_HOST:-/tmp} -p ${POSTGRES_PORT:-5433} -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-ongero_check}"
if ! $PSQL -q -c 'TRUNCATE subscriptions, payments, users CASCADE' 2>/dev/null; then
  echo "Could not reach PostgreSQL. See the header of this script."
  exit 1
fi

redis-server --port "$REDIS_PORT" --save '' --appendonly no --daemonize no > /tmp/ongero-redis.log 2>&1 &
REDIS_PID=$!

cd "$ROOT/backend/rest-api" || exit 1
POSTGRES_HOST="${POSTGRES_HOST:-/tmp}" POSTGRES_PORT="${POSTGRES_PORT:-5433}" \
POSTGRES_USER="${POSTGRES_USER:-postgres}" POSTGRES_DB="${POSTGRES_DB:-ongero_check}" \
JWT_SECRET="$SECRET" PORT="$API_PORT" \
  ./node_modules/.bin/ts-node -T src/main.ts > /tmp/ongero-auth-api.log 2>&1 &
API_PID=$!

cd "$ROOT/backend/ws-signaling" || exit 1
# Built, not `go run`: that wraps the server in a parent process, and killing the
# parent orphans the server rather than stopping it.
if ! go build -o /tmp/ongero-ws-server ./cmd/server; then
  echo "go build failed"; exit 1
fi
JWT_SECRET="$SECRET" WS_PORT="$WS_PORT" REDIS_URL="redis://127.0.0.1:$REDIS_PORT" \
  /tmp/ongero-ws-server > /tmp/ongero-ws.log 2>&1 &
WS_PID=$!

for _ in $(seq 1 90); do
  curl -sf "$API/health" > /dev/null 2>&1 && curl -sf "http://127.0.0.1:$WS_PORT/health" > /dev/null 2>&1 && break
  sleep 1
done
if ! curl -sf "$API/health" > /dev/null 2>&1; then
  echo "REST API did not start:"; tail -20 /tmp/ongero-auth-api.log; exit 1
fi
if ! curl -sf "http://127.0.0.1:$WS_PORT/health" > /dev/null 2>&1; then
  echo "Signalling server did not start:"; tail -20 /tmp/ongero-ws.log; exit 1
fi
echo "1. REST API on $API_PORT and signalling on $WS_PORT, sharing one JWT secret"
echo

# ---- the app's own authentication ---------------------------------------------------
AUTH="$(curl -s -X POST "$API/auth/anonymous" -H 'Content-Type: application/json' \
  --data-raw '{"deviceId":"signalling-check-device","locale":"en"}')"
TOKEN="$(printf '%s' "$AUTH" | python3 -I -c 'import json,sys;print(json.load(sys.stdin).get("accessToken",""))')"
USER_ID="$(printf '%s' "$AUTH" | python3 -I -c 'import json,sys;print(json.load(sys.stdin)["user"]["id"])')"
check "2. the REST API issues a token" "yes" "$([ -n "$TOKEN" ] && echo yes || echo "no: $AUTH")"

# ---- what the broken client did ------------------------------------------------------
check "3. no token is refused" "STATUS 401" \
  "$(python3 -I "$PROBE" 127.0.0.1 "$WS_PORT" /ws | head -1)"

# ---- and what an attacker would try --------------------------------------------------
FORGED="$(python3 -I - <<'PY'
import base64, hashlib, hmac, json, time
def seg(d): return base64.urlsafe_b64encode(json.dumps(d,separators=(",",":")).encode()).rstrip(b"=")
head, body = seg({"alg":"HS256","typ":"JWT"}), seg({"sub":"intruder","exp":int(time.time())+600})
sig = base64.urlsafe_b64encode(hmac.new(b"the-wrong-secret", head+b"."+body, hashlib.sha256).digest()).rstrip(b"=")
print((head+b"."+body+b"."+sig).decode())
PY
)"
check "4. a token signed with the wrong secret is refused" "STATUS 401" \
  "$(python3 -I "$PROBE" 127.0.0.1 "$WS_PORT" /ws "$FORGED" | head -1)"

EXPIRED="$(python3 -I - <<PY
import base64, hashlib, hmac, json, time
def seg(d): return base64.urlsafe_b64encode(json.dumps(d,separators=(",",":")).encode()).rstrip(b"=")
head, body = seg({"alg":"HS256","typ":"JWT"}), seg({"sub":"$USER_ID","exp":int(time.time())-60})
sig = base64.urlsafe_b64encode(hmac.new(b"$SECRET", head+b"."+body, hashlib.sha256).digest()).rstrip(b"=")
print((head+b"."+body+b"."+sig).decode())
PY
)"
check "5. an expired token is refused" "STATUS 401" \
  "$(python3 -I "$PROBE" 127.0.0.1 "$WS_PORT" /ws "$EXPIRED" | head -1)"

# ---- the fix -------------------------------------------------------------------------
OUT="$(python3 -I "$PROBE" 127.0.0.1 "$WS_PORT" /ws "$TOKEN")"
check "6. a real token is accepted" "STATUS 101" "$(printf '%s' "$OUT" | grep '^STATUS' | head -1)"
check "   the server greets the client" "connected" \
  "$(printf '%s' "$OUT" | sed -n 's/^TYPE //p' | head -1)"
check "   and addresses it by the server's own user id" "$USER_ID" \
  "$(printf '%s' "$OUT" | sed -n 's/^CLIENTID //p' | head -1)"

echo
if [ "$failures" -eq 0 ]; then echo "PASS: 0 failed check(s)"; else echo "FAIL: $failures failed check(s)"; fi
exit $([ "$failures" -eq 0 ] && echo 0 || echo 1)
