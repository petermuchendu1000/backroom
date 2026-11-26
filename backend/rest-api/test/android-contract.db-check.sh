#!/usr/bin/env bash
#
# Does the Android client's request actually work against the real server?
#
# Unit tests on either side cannot answer that. The Kotlin tests assert what the client
# sends; the Nest tests assert what the server accepts; both pass while the two
# disagree about a field name or a plan value. This replays the EXACT bytes the Android
# client produced — written to app/build/android-contract/ by PaymentsApiTest — against
# a running API, and walks a payment all the way to an active subscription.
#
# PalPluss is stubbed, not called. The point here is the contract between our two
# halves, and nobody should be sending real STK prompts from a test.
#
# Prerequisites: PostgreSQL with the three migrations applied, and
#   cd app && ../gradlew :app:testDebugUnitTest   (to produce the request files)
#
# Usage:
#   backend/rest-api/test/android-contract.db-check.sh
#
set -o pipefail
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CONTRACT_DIR="$ROOT/app/build/android-contract"
API_PORT="${API_PORT:-8098}"
STUB_PORT="${STUB_PORT:-8097}"
API="http://127.0.0.1:$API_PORT/api/v1"
WEBHOOK_SECRET="whsec_$(printf 'ongero-contract-check-secret' | base64 -w0)"

failures=0
check() { # label expected actual
  if [ "$2" = "$3" ]; then
    echo "PASS  $1"
  else
    echo "FAIL  $1"
    echo "      expected: $2"
    echo "      actual:   $3"
    failures=$((failures + 1))
  fi
}

for f in auth-request.json pay-request.json; do
  if [ ! -f "$CONTRACT_DIR/$f" ]; then
    echo "MISSING $CONTRACT_DIR/$f — run the Android unit tests first:"
    echo "  sh gradlew :app:testDebugUnitTest"
    exit 1
  fi
done

AUTH_BODY="$(cat "$CONTRACT_DIR/auth-request.json")"
PAY_BODY="$(cat "$CONTRACT_DIR/pay-request.json")"
echo "Android auth body: $AUTH_BODY"
echo "Android pay  body: $PAY_BODY"
echo

# Start from an empty database every time.
#
# Without this the check inherits state: the client's deviceId is constant, so a second
# run authenticates as the SAME user the first run upgraded to Plus, and the final
# assertion passes whether or not this run's payment worked. That was observed — a
# deliberately broken client still "passed" step 7 — so the reset is part of the test,
# not tidiness.
PSQL="psql -h ${POSTGRES_HOST:-/tmp} -p ${POSTGRES_PORT:-5433} -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-ongero_check}"
if ! $PSQL -q -c 'TRUNCATE subscriptions, payments, users CASCADE' 2>/dev/null; then
  echo "Could not reach the database to reset it. Is PostgreSQL running with the"
  echo "migrations applied? See the header of this script."
  exit 1
fi

cleanup() {
  [ -n "${API_PID:-}" ] && kill "$API_PID" 2>/dev/null
  [ -n "${STUB_PID:-}" ] && kill "$STUB_PID" 2>/dev/null
  wait 2>/dev/null
}
trap cleanup EXIT

# ---- a stand-in for PalPluss ------------------------------------------------------
python3 -I "$ROOT/backend/rest-api/test/palpluss-stub.py" "$STUB_PORT" &
STUB_PID=$!

# ---- the real API -----------------------------------------------------------------
cd "$ROOT/backend/rest-api" || exit 1
POSTGRES_HOST="${POSTGRES_HOST:-/tmp}" POSTGRES_PORT="${POSTGRES_PORT:-5433}" \
POSTGRES_USER="${POSTGRES_USER:-postgres}" POSTGRES_DB="${POSTGRES_DB:-ongero_check}" \
JWT_SECRET=contract_check_not_a_real_secret \
PORT="$API_PORT" \
PAYMENTS_PROVIDER=palpluss \
PALPLUSS_BASE_URL="http://127.0.0.1:$STUB_PORT/v1" \
PALPLUSS_API_KEY=pp_test_contract_check \
PALPLUSS_CALLBACK_URL="http://127.0.0.1:$API_PORT/api/v1/payments/palpluss/webhook" \
PALPLUSS_WEBHOOK_SECRET="$WEBHOOK_SECRET" \
  npx ts-node -T src/main.ts > /tmp/ongero-contract-api.log 2>&1 &
API_PID=$!

for _ in $(seq 1 60); do
  curl -sf "$API/health" > /dev/null 2>&1 && break
  sleep 1
done
if ! curl -sf "$API/health" > /dev/null 2>&1; then
  echo "API did not start. Last lines:"; tail -25 /tmp/ongero-contract-api.log; exit 1
fi
echo "1. API up on $API_PORT, PalPluss stubbed on $STUB_PORT"
echo

# ---- replay the client's auth request ---------------------------------------------
AUTH_RESPONSE="$(curl -s -X POST "$API/auth/anonymous" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  --data-raw "$AUTH_BODY")"
TOKEN="$(printf '%s' "$AUTH_RESPONSE" | python3 -I -c 'import json,sys;print(json.load(sys.stdin).get("accessToken",""))')"
check "2. the client's auth body is accepted" "yes" "$([ -n "$TOKEN" ] && echo yes || echo "no: $AUTH_RESPONSE")"

# ---- replay the client's pay request ----------------------------------------------
PAY_CODE="$(curl -s -o /tmp/ongero-pay.json -w '%{http_code}' -X POST "$API/payments/pay" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  -H "Authorization: Bearer $TOKEN" --data-raw "$PAY_BODY")"
check "3. the client's pay body is accepted" "201" "$PAY_CODE"
PAY_RESPONSE="$(cat /tmp/ongero-pay.json)"

# Every field the Kotlin InitiatePaymentResponse declares must actually be present;
# Gson fills a missing one with null and the client would fail much later.
for field in paymentRef reference plan amount message provider; do
  HAS="$(printf '%s' "$PAY_RESPONSE" | python3 -I -c "import json,sys;print('yes' if '$field' in json.load(sys.stdin) else 'no')" 2>/dev/null)"
  check "   response carries '$field'" "yes" "$HAS"
done

REF="$(printf '%s' "$PAY_RESPONSE" | python3 -I -c 'import json,sys;print(json.load(sys.stdin).get("paymentRef",""))')"

# ---- the status endpoint the client polls -----------------------------------------
STATUS="$(curl -s "$API/payments/$REF/status" -H "Authorization: Bearer $TOKEN" \
  | python3 -I -c 'import json,sys;print(json.load(sys.stdin).get("status",""))')"
check "4. status before payment" "pending" "$STATUS"

# ---- the gateway reports success ---------------------------------------------------
python3 -I "$ROOT/backend/rest-api/test/send-palpluss-webhook.py" \
  "http://127.0.0.1:$API_PORT/api/v1/payments/palpluss/webhook" "$REF" 500 "$WEBHOOK_SECRET" \
  > /tmp/ongero-webhook.out 2>&1
check "5. the signed webhook is accepted" "200" "$(cat /tmp/ongero-webhook.out)"

AFTER="$(curl -s "$API/payments/$REF/status" -H "Authorization: Bearer $TOKEN")"
check "6. status after payment" "succeeded" \
  "$(printf '%s' "$AFTER" | python3 -I -c 'import json,sys;print(json.load(sys.stdin).get("status",""))')"
check "   succeeded flag the client reads" "True" \
  "$(printf '%s' "$AFTER" | python3 -I -c 'import json,sys;print(json.load(sys.stdin).get("succeeded"))')"

# ---- and the subscription the client is buying -------------------------------------
check "7. the user is now on plus" "plus" \
  "$(curl -s "$API/auth/me" -H "Authorization: Bearer $TOKEN" \
     | python3 -I -c 'import json,sys;print(json.load(sys.stdin).get("subscriptionTier",""))')"

echo
if [ "$failures" -eq 0 ]; then echo "PASS: 0 failed check(s)"; else echo "FAIL: $failures failed check(s)"; fi
exit $([ "$failures" -eq 0 ] && echo 0 || echo 1)
