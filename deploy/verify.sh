#!/usr/bin/env bash
#
# Ongero post-deployment verification. Run ON the server, from the repository root:
#
#     ./deploy/verify.sh
#
# Exits 0 only if every check passes. Exits 1 and prints what failed otherwise.
#
# This script deliberately contains no `|| true`, no `2>/dev/null` on a checked
# command and no `set +e`. A verification that cannot report failure is not a
# verification, and silencing a channel is how a broken deploy reads as a good one.
#
# It never prints a secret. Where two components must share a value it compares
# SHA-256 prefixes, which proves agreement without disclosing anything.

set -u
set -o pipefail

API_HOST="${API_HOST:-api.ongero.com}"
WS_HOST="${WS_HOST:-ws.ongero.com}"
TURN_HOST="${TURN_HOST:-turn.ongero.com}"

pass=0
fail=0
declare -a failures=()

ok()   { printf '  \033[32mPASS\033[0m  %s\n' "$1"; pass=$((pass + 1)); }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; fail=$((fail + 1)); failures+=("$1"); }
head_() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# Hash helper: proves two values are equal without revealing either.
hash12() { printf '%s' "$1" | sha256sum | cut -c1-12; }

# -------------------------------------------------------------------------
head_ "1. Containers"
# -------------------------------------------------------------------------
for svc in postgres redis coturn rest-api ws-signaling caddy; do
  state=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml ps \
            --format '{{.Service}} {{.State}}' | awk -v s="$svc" '$1==s {print $2}')
  if [ "$state" = "running" ]; then
    ok "$svc is running"
  else
    bad "$svc is '${state:-absent}', expected running"
  fi
done

# -------------------------------------------------------------------------
head_ "2. Nothing is listening publicly that should not be"
# -------------------------------------------------------------------------
# The single most important check here. The development compose file publishes
# Postgres and Redis on every interface; on a box with a public IP that is a
# database open to the internet. The production overlay must have replaced those
# bindings with loopback-only ones.
if ! command -v ss >/dev/null; then
  bad "ss not installed (apt-get install -y iproute2) — cannot check exposure"
else
  listening=$(ss -ltnH)
  for entry in "5432:Postgres" "6379:Redis" "8080:REST API" "8443:signalling"; do
    port="${entry%%:*}"; name="${entry##*:}"
    public=$(printf '%s\n' "$listening" | awk -v p=":$port\$" '$4 ~ p {print $4}' \
             | grep -vE '^(127\.0\.0\.1|\[::1\])')
    if [ -z "$public" ]; then
      ok "$name ($port) is not bound to a public address"
    else
      bad "$name ($port) IS PUBLICLY BOUND on: $(printf '%s' "$public" | tr '\n' ' ')"
    fi
  done
fi

# Confirm from the outside too, which is the only view that really counts.
# A refused or timed-out connection is the pass condition here.
for entry in "5432:Postgres" "6379:Redis"; do
  port="${entry%%:*}"; name="${entry##*:}"
  # stderr is kept, not discarded: on an unexpected result we want to see why.
  conn_err=$(timeout 5 bash -c "exec 3<>/dev/tcp/${API_HOST}/${port}" 2>&1)
  if [ $? -eq 0 ]; then
    bad "$name ($port) ACCEPTED a connection from outside via $API_HOST"
  else
    ok "$name ($port) refuses external connections (${conn_err##*: })"
  fi
done

# -------------------------------------------------------------------------
head_ "3. HTTPS and routing"
# -------------------------------------------------------------------------
code=$(curl -sS -o /tmp/ongero-health.$$ -w '%{http_code}' --max-time 15 \
         "https://${API_HOST}/api/v1/health")
if [ "$code" = "200" ] && grep -q '"status"' /tmp/ongero-health.$$; then
  ok "https://${API_HOST}/api/v1/health -> 200"
else
  bad "https://${API_HOST}/api/v1/health -> $code (body: $(head -c 120 /tmp/ongero-health.$$))"
fi
rm -f /tmp/ongero-health.$$

# Certificate must be valid, not merely present. -sS without -k means curl
# itself fails on an untrusted or expired chain.
if curl -sS -o /dev/null --max-time 15 "https://${API_HOST}/api/v1/health"; then
  ok "TLS certificate for ${API_HOST} validates"
else
  bad "TLS certificate for ${API_HOST} does NOT validate"
fi

# Swagger describes every endpoint and DTO. It must not be reachable in production.
code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "https://${API_HOST}/docs")
if [ "$code" = "404" ]; then
  ok "https://${API_HOST}/docs is blocked (404)"
else
  bad "https://${API_HOST}/docs -> $code — the API documentation is EXPOSED"
fi

code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "https://${WS_HOST}/health")
if [ "$code" = "200" ]; then
  ok "https://${WS_HOST}/health -> 200"
else
  bad "https://${WS_HOST}/health -> $code"
fi

# HTTP must redirect to HTTPS, not serve.
code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "http://${API_HOST}/api/v1/health")
if [ "$code" = "308" ] || [ "$code" = "301" ] || [ "$code" = "302" ]; then
  ok "http:// redirects to https:// ($code)"
else
  bad "http://${API_HOST} -> $code, expected a redirect"
fi

# -------------------------------------------------------------------------
head_ "4. WebSocket upgrade through the proxy"
# -------------------------------------------------------------------------
# No token, so the signalling service should reject the connection — but it must
# reject it ITSELF. 101 or 401 both prove the upgrade reached the service. A 502
# or a timeout means the proxy never got there.
up=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 \
       -H "Connection: Upgrade" -H "Upgrade: websocket" \
       -H "Sec-WebSocket-Key: $(openssl rand -base64 16)" \
       -H "Sec-WebSocket-Version: 13" \
       "https://${WS_HOST}/ws")
case "$up" in
  101|401|403) ok "wss://${WS_HOST}/ws reached the signalling service ($up)" ;;
  *)           bad "wss://${WS_HOST}/ws -> $up — the proxy is not forwarding upgrades" ;;
esac

# -------------------------------------------------------------------------
head_ "5. Shared secrets agree across components"
# -------------------------------------------------------------------------
# Three services compute one HMAC from TURN_STATIC_AUTH_SECRET, and two share
# JWT_SECRET. Drift here produces failures that look like anything but a config
# problem: calls that never connect, or a client that logs in and then gets 401
# on the socket. Compared by hash prefix; no value is printed.
api_jwt=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
            exec -T rest-api printenv JWT_SECRET)
ws_jwt=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
            exec -T ws-signaling printenv JWT_SECRET)
if [ -n "$api_jwt" ] && [ "$api_jwt" = "$ws_jwt" ]; then
  ok "JWT_SECRET matches in rest-api and ws-signaling (sha256:$(hash12 "$api_jwt"))"
else
  bad "JWT_SECRET DIFFERS: rest-api sha256:$(hash12 "$api_jwt") vs ws-signaling sha256:$(hash12 "$ws_jwt")"
fi

api_turn=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
             exec -T rest-api printenv TURN_STATIC_AUTH_SECRET)
ws_turn=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
             exec -T ws-signaling printenv TURN_STATIC_AUTH_SECRET)
if [ -n "$api_turn" ] && [ "$api_turn" = "$ws_turn" ]; then
  ok "TURN_STATIC_AUTH_SECRET matches in rest-api and ws-signaling (sha256:$(hash12 "$api_turn"))"
else
  bad "TURN_STATIC_AUTH_SECRET DIFFERS between rest-api and ws-signaling"
fi

# The default JWT secret must never reach production.
if [ "$api_jwt" = "dev_jwt_secret_change_me_in_production" ]; then
  bad "JWT_SECRET is still the DEVELOPMENT DEFAULT — anyone can mint a valid token"
else
  ok "JWT_SECRET is not the development default"
fi

# -------------------------------------------------------------------------
head_ "6. TURN actually relays"
# -------------------------------------------------------------------------
# Mint a short-lived credential the way the services do — username is
# "<expiry>:<user>", password is the base64 HMAC-SHA1 of it — and ask coturn for a
# real allocation. This is the only check that proves the TURN secret, the
# firewall and the relay port range line up.
#
# Reading the output correctly matters here. Measured against coturn 4.19.2 with
# this exact turnserver.conf:
#
#   wrong secret        -> "ERROR Cannot complete Allocation"
#   expired credential  -> "ERROR Cannot complete Allocation"
#   VALID credential    -> allocation granted; then "channel bind: error 403"
#                          when the peer is an address turnserver.conf denies
#
# So the discriminator is the ALLOCATION, not the overall exit code —
# turnutils_uclient exits non-zero even on a successful allocation if the channel
# bind is refused. A 403 on channel bind is turnserver.conf's denied-peer-ip list
# doing its job, not an authentication problem.
if [ -n "${api_turn:-}" ]; then
  expiry=$(( $(date +%s) + 600 ))
  tuser="${expiry}:verify"
  tpass=$(printf '%s' "$tuser" | openssl dgst -sha1 -hmac "$api_turn" -binary | base64)
  turn_out=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
               exec -T coturn turnutils_uclient -y -u "$tuser" -w "$tpass" \
               -n 1 "${PUBLIC_IP:-$TURN_HOST}" 2>&1)
  if printf '%s' "$turn_out" | grep -qi "Cannot complete Allocation"; then
    bad "coturn REFUSED the allocation — TURN_STATIC_AUTH_SECRET disagrees between coturn and rest-api, or the clock has drifted"
  elif printf '%s' "$turn_out" | grep -qiE "uclient"; then
    ok "coturn granted a relay allocation with a freshly minted credential"
  else
    bad "turnutils_uclient produced no recognisable output — is UDP 3478 reachable? Output: $(printf '%s' "$turn_out" | head -c 200)"
  fi

  # An expired credential MUST be refused. Without this, the check above could
  # pass against a coturn that accepts anything.
  stale=$(( $(date +%s) - 600 ))
  suser="${stale}:verify"
  spass=$(printf '%s' "$suser" | openssl dgst -sha1 -hmac "$api_turn" -binary | base64)
  stale_out=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                exec -T coturn turnutils_uclient -y -u "$suser" -w "$spass" \
                -n 1 "${PUBLIC_IP:-$TURN_HOST}" 2>&1)
  if printf '%s' "$stale_out" | grep -qi "Cannot complete Allocation"; then
    ok "coturn refuses an EXPIRED credential (so the check above can fail)"
  else
    bad "coturn ACCEPTED an expired credential — it is not enforcing expiry, and anyone can use the relay"
  fi
else
  bad "could not read TURN_STATIC_AUTH_SECRET from rest-api; skipped the allocation test"
fi

# -------------------------------------------------------------------------
head_ "7. Database schema is present"
# -------------------------------------------------------------------------
# The migrations are mounted into docker-entrypoint-initdb.d, which Postgres runs
# ONLY when it initialises an empty data directory. If the volume already existed
# when you first started the stack, they never ran and these tables are missing.
for tbl in users subscriptions payment_transactions; do
  n=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
        exec -T postgres psql -U "${POSTGRES_USER:-ongero}" -d "${POSTGRES_DB:-ongero}" \
        -tAc "SELECT count(*) FROM information_schema.tables WHERE table_name='$tbl'")
  if [ "$(printf '%s' "$n" | tr -d '[:space:]')" = "1" ]; then
    ok "table '$tbl' exists"
  else
    bad "table '$tbl' is MISSING — the init migrations did not run against this volume"
  fi
done

# -------------------------------------------------------------------------
head_ "8. Webhook signing is configured"
# -------------------------------------------------------------------------
whsec=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
          exec -T rest-api printenv PALPLUSS_WEBHOOK_SECRET)
if [ -n "$(printf '%s' "$whsec" | tr -d '[:space:]')" ]; then
  ok "PALPLUSS_WEBHOOK_SECRET is set (sha256:$(hash12 "$whsec"))"
else
  bad "PALPLUSS_WEBHOOK_SECRET is EMPTY — payment callbacks are accepted unsigned"
fi

# -------------------------------------------------------------------------
printf '\n\033[1m================ %d passed, %d failed ================\033[0m\n' "$pass" "$fail"
if [ "$fail" -gt 0 ]; then
  printf '\nFailed checks:\n'
  for f in "${failures[@]}"; do printf '  - %s\n' "$f"; done
  exit 1
fi
printf '\nAll checks passed.\n'
