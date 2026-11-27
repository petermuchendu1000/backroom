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
# Reading the output correctly is the whole difficulty, and getting it wrong is
# easy. Three traps, each measured against coturn 4.19.2 with this turnserver.conf:
#
#  1. The exit code is useless. turnutils_uclient exits non-zero even on a
#     SUCCESSFUL allocation, because turnserver.conf's denied-peer-ip list
#     refuses the channel bind with a 403 — which is that list working, not an
#     authentication failure.
#
#  2. Absence of the error string is NOT success. An earlier version of this
#     script treated "no 'Cannot complete Allocation'" as a pass. Measured: a
#     connection to a DEAD PORT produces neither the error nor any allocation,
#     so it passed. So did a TLS attempt against a port with no TLS listener.
#     The check must require POSITIVE evidence.
#
#  3. "Received relay addr" only appears with -v. Without it the client prints
#     almost nothing on success.
#
# So: require "Received relay addr", the line where the server hands over an
# actual relay address, and pass -v so it is printed at all.
#
# -t forces TCP. Without it the client uses UDP, and with -S that means DTLS,
# which coturn documents as not started by default — so a TURNS check without
# -t fails with ECONNREFUSED against a perfectly healthy TLS listener.
#  4. Every probe must use a DIFFERENT account id. turnserver.conf sets
#     user-quota=12, allocations live for up to 600s, and coturn counts the
#     quota against the account id after the colon — not the whole username.
#     Reusing one id makes the first probes succeed and later ones fail with
#     STUN error 486 (Allocation Quota Reached), so running this script a few
#     times in a row would report TURNS as broken when it is fine. Measured:
#     reused id -> error 486 and no relay; unique id -> 5 relay addresses, three
#     runs out of three.
turn_probe() {
  # turn_probe <secret> <expiry-offset-seconds> <extra-uclient-args...>
  local secret="$1" offset="$2"; shift 2
  local exp u p
  exp=$(( $(date +%s) + offset ))
  # Unique per probe AND per run of this script, so nothing accumulates.
  u="${exp}:vfy-$$-${RANDOM}"
  p=$(printf '%s' "$u" | openssl dgst -sha1 -hmac "$secret" -binary | base64)
  docker compose -f docker-compose.yml -f docker-compose.prod.yml \
    exec -T coturn turnutils_uclient -v -y "$@" -u "$u" -w "$p" \
    -n 1 "${PUBLIC_IP:-$TURN_HOST}" 2>&1
}
granted()  { printf '%s' "$1" | grep -q "Received relay addr"; }
refused()  { printf '%s' "$1" | grep -q "Cannot complete Allocation"; }
over_quota() { printf '%s' "$1" | grep -q "error 486"; }

if [ -n "${api_turn:-}" ]; then
  # --- plain TURN on 3478: the critical path -------------------------------
  out=$(turn_probe "$api_turn" 600)
  if granted "$out"; then
    ok "coturn granted a relay allocation on 3478 with a freshly minted credential"
  elif over_quota "$out"; then
    bad "coturn hit the allocation quota (STUN 486) on 3478 — check user-quota/total-quota in turnserver.conf"
  elif refused "$out"; then
    bad "coturn REFUSED the allocation on 3478 — TURN_STATIC_AUTH_SECRET disagrees between coturn and rest-api, or the clock has drifted"
  else
    bad "no allocation and no refusal on 3478 — is the port reachable? Output: $(printf '%s' "$out" | tail -c 200)"
  fi

  # An expired credential MUST be refused, or the check above proves nothing.
  out=$(turn_probe "$api_turn" -600)
  if refused "$out"; then
    ok "coturn refuses an EXPIRED credential (so the check above can fail)"
  elif granted "$out"; then
    bad "coturn ACCEPTED an expired credential — it is not enforcing expiry, and anyone can use the relay"
  else
    bad "expired-credential probe produced neither signal; the allocation check above cannot be trusted"
  fi

  # A wrong secret MUST be refused too. Catches a coturn started without the
  # shared secret reaching it at all.
  out=$(turn_probe "definitely-not-the-turn-secret" 600)
  if refused "$out"; then
    ok "coturn refuses a WRONG secret"
  else
    bad "coturn did not refuse a wrong secret — authentication is not being enforced"
  fi

  # --- TURNS on 5349: TCP + TLS -------------------------------------------
  # Two independent channels, because they fail for different reasons and the
  # TURN client's message for a TLS problem does not say "TLS".
  #
  # Channel 1: the TLS listener itself, with no TURN semantics involved. The
  # certificate comes from Let's Encrypt, so it verifies against the system
  # trust store; -verify_return_error makes openssl FAIL on a bad chain instead
  # of reporting it and carrying on.
  tls_out=$(printf '' | timeout 15 openssl s_client -connect "${TURN_HOST}:5349" \
              -servername "$TURN_HOST" -verify_return_error 2>&1)
  if printf '%s' "$tls_out" | grep -q "Verify return code: 0 (ok)"; then
    san=$(printf '%s' "$tls_out" | openssl x509 -noout -ext subjectAltName 2>/dev/null | tail -1 | tr -d ' ')
    ok "TURNS TLS listener on ${TURN_HOST}:5349 presents a valid certificate (${san:-SAN unavailable})"
  elif printf '%s' "$tls_out" | grep -q "CONNECTED"; then
    bad "5349 accepts TLS but the certificate does NOT verify: $(printf '%s' "$tls_out" | grep -iE 'verify (error|return)' | head -1)"
  else
    bad "nothing is listening for TLS on ${TURN_HOST}:5349 — is UDP/TCP 5349 open, and did cert-sync install a certificate?"
  fi

  # Channel 2: a real TURN allocation through that listener. -t forces TCP; with
  # UDP, -S means DTLS, which coturn does not start by default.
  out=$(turn_probe "$api_turn" 600 -t -S -p 5349)
  if granted "$out"; then
    ok "TURNS granted a relay allocation over TCP+TLS on 5349"
  elif over_quota "$out"; then
    bad "TURNS hit the allocation quota (STUN 486) — unexpected with a unique account id; is user-quota in turnserver.conf very low, or is something else minting allocations?"
  elif refused "$out"; then
    bad "TURNS listener is up but refused the credential on 5349"
  else
    bad "TURNS is NOT working on 5349 — clients on UDP-blocking networks cannot relay. Check 'docker compose logs coturn' and 'docker compose logs cert-sync'. Output: $(printf '%s' "$out" | tail -c 200)"
  fi
else
  bad "could not read TURN_STATIC_AUTH_SECRET from rest-api; skipped every allocation test"
fi

# -------------------------------------------------------------------------
head_ "6b. TURN certificate and logging"
# -------------------------------------------------------------------------
# coturn cannot obtain its own certificate, so Caddy gets one for the TURN
# hostname and cert-sync re-owns it: Caddy writes root:root mode 600 and coturn
# runs as nobody, so without that copy the file is present and unreadable.
cs_log=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=50 cert-sync)
if printf '%s' "$cs_log" | grep -q "installed certificate"; then
  ok "cert-sync installed a certificate: $(printf '%s' "$cs_log" | grep 'installed certificate' | tail -1 | sed 's/.*(\(expires[^)]*\)).*/\1/')"
else
  bad "cert-sync has not installed a certificate — TURNS cannot work. Is there a site block for \$TURN_HOST in the Caddyfile, and does it resolve here?"
fi

# The logging fix. This previously returned ZERO lines: turnserver.conf set
# no-stdout-log with no log-file, so coturn wrote to /var/tmp inside the
# container — invisible to docker logs and destroyed with the container. The
# startup errors explaining why TURNS was dead were in there, unread.
co_log=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=50 coturn)
if [ -n "$(printf '%s' "$co_log" | tr -d '[:space:]')" ]; then
  ok "coturn logs are visible to docker ($(printf '%s' "$co_log" | wc -l) lines)"
else
  bad "coturn produced NO log output — has no-stdout-log come back, or log-file=stdout been removed?"
fi

# coturn says this explicitly when the certificate is missing or unreadable.
if printf '%s' "$co_log" | grep -q "tls-listening-port .* is configured, but the TLS and DTLS listeners are disabled"; then
  bad "coturn reports the TLS listener is DISABLED — the certificate is missing or not readable by uid 65534"
else
  ok "coturn reports no TLS listener failure"
fi

# These three were silently rejected by coturn 4.19.2 for as long as they
# existed, and no-stdout-log is why nobody saw it.
if printf '%s' "$co_log" | grep -q "Bad configuration format"; then
  bad "coturn REJECTED configuration directives: $(printf '%s' "$co_log" | grep -o 'Bad configuration format: [a-z0-9_-]*' | sort -u | tr '\n' ' ')"
else
  ok "coturn accepted every directive in turnserver.conf"
fi

# -------------------------------------------------------------------------
head_ "6c. coturn image is pinned, and pinned consistently"
# -------------------------------------------------------------------------
# The coturn image is referenced in two files — the coturn service in
# docker-compose.yml and cert-sync in docker-compose.prod.yml. Two literals can
# drift, so this asserts they have not, rather than trusting the comment that
# says they must not.
#
# Pinning matters here because the configuration is version-sensitive in ways
# that fail quietly: coturn 4.19.2 rejects directives older versions accepted,
# and an unpinned tag changes TURN behaviour with no diff.
C="-f docker-compose.yml -f docker-compose.prod.yml"

# .Config.Image is the reference as written; .Image is the resolved content id.
co_ref=$(docker inspect --format '{{.Config.Image}}' "$(docker compose $C ps -q coturn)")
cs_ref=$(docker inspect --format '{{.Config.Image}}' "$(docker compose $C ps -q cert-sync)")
co_id=$(docker inspect --format '{{.Image}}' "$(docker compose $C ps -q coturn)")
cs_id=$(docker inspect --format '{{.Image}}' "$(docker compose $C ps -q cert-sync)")

case "$co_ref" in
  *@sha256:*) ok "coturn is pinned by digest (${co_ref##*@})" ;;
  *)          bad "coturn is NOT pinned — it references the mutable tag '$co_ref', so a rebuild can change the TURN server with no diff" ;;
esac

if [ "$co_id" = "$cs_id" ]; then
  ok "coturn and cert-sync run the same image"
else
  bad "coturn and cert-sync run DIFFERENT images ($co_ref vs $cs_ref) — the two pins have drifted apart"
fi

# The pinned image must be the version this configuration was written against.
# turnserver.conf's comments and deploy/coturn-entrypoint.sh both record
# behaviour measured on 4.19.2.
co_ver=$(docker compose $C exec -T coturn turnserver --version 2>&1 | tr -d '[:space:]')
if [ "$co_ver" = "4.19.2" ]; then
  ok "coturn is 4.19.2, the version this configuration was verified against"
else
  bad "coturn reports version '$co_ver', not the 4.19.2 this configuration was verified against — re-check turnserver.conf for rejected directives and re-read 'turnserver -h' for the cert and log options"
fi

# -------------------------------------------------------------------------
head_ "7. Database schema is present"
# -------------------------------------------------------------------------
# The migrations are mounted into docker-entrypoint-initdb.d, which Postgres runs
# ONLY when it initialises an empty data directory. If the volume already existed
# when you first started the stack, they never ran and these tables are missing.
for tbl in users subscriptions payments; do
  n=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
        exec -T postgres psql -U "${POSTGRES_USER:-ongero}" -d "${POSTGRES_DB:-ongero}" \
        -tAc "SELECT count(*) FROM information_schema.tables WHERE table_name='$tbl'")
  if [ "$(printf '%s' "$n" | tr -d '[:space:]')" = "1" ]; then
    ok "table '$tbl' exists"
  else
    bad "table '$tbl' is MISSING — the init migrations did not run against this volume"
  fi
done

# 002_mpesa_payments.sql CREATEs payments; 003_payment_gateways.sql only ALTERs it.
# Checking for the table cannot tell those two apart, so a stack where 03- never ran
# would pass the loop above and then fail at runtime the first time the PalPluss code
# writes a reference. Check a column only 003 adds.
for col in provider reference; do
  n=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
        exec -T postgres psql -U "${POSTGRES_USER:-ongero}" -d "${POSTGRES_DB:-ongero}" \
        -tAc "SELECT count(*) FROM information_schema.columns WHERE table_name='payments' AND column_name='$col'")
  if [ "$(printf '%s' "$n" | tr -d '[:space:]')" = "1" ]; then
    ok "column payments.$col exists (03-payment-gateways.sql ran)"
  else
    bad "column payments.$col is MISSING — 03-payment-gateways.sql did not run against this volume"
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
head_ "9. Firebase points at one project, and the credentials work"
# -------------------------------------------------------------------------
# Several things have to name the same Firebase project and NOTHING fails loudly
# when they disagree. ws-signaling interpolates the id into
#   https://fcm.googleapis.com/v1/projects/<id>/messages:send
# and an FCM token is scoped to the project the APK registered with. A mismatch
# therefore delivers nothing while every service still logs a successful init —
# which is exactly what happened before 2494ea4: FIREBASE_PROJECT_ID was never set,
# the code fell back to a hardcoded "backroom-b51b1", and the log said
# "FCM client initialized for push notifications".
#
# NOT CHECKED HERE: app/google-services.json. The copy in this checkout is the
# committed placeholder; the one that matters is on whichever machine builds the
# APK. Check it there:
#   grep -o '"project_id": *"[^"]*"' app/google-services.json
fcm_ws=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
           exec -T ws-signaling printenv FCM_PROJECT_ID | tr -d '[:space:]')
fcm_api=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
            exec -T rest-api printenv FCM_PROJECT_ID | tr -d '[:space:]')

if [ -n "$fcm_ws" ]; then
  ok "ws-signaling has FCM_PROJECT_ID=$fcm_ws"
else
  bad "ws-signaling has no FCM_PROJECT_ID — pushes cannot be addressed"
fi

if [ "$fcm_ws" = "$fcm_api" ]; then
  ok "rest-api agrees on the project id"
else
  bad "project id disagrees: ws-signaling='$fcm_ws' rest-api='$fcm_api'"
fi

key=secrets/firebase-service-account.json
if [ -f "$key" ]; then
  fcm_key=$(grep -o '"project_id"[[:space:]]*:[[:space:]]*"[^"]*"' "$key" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
  if [ "$fcm_key" = "$fcm_ws" ]; then
    ok "the service account key belongs to $fcm_key"
  else
    bad "the service account key is for '$fcm_key' but the services send to '$fcm_ws' — every push will be 403"
  fi
else
  bad "$key is missing — no phone will ever ring"
fi

# The one that can only pass if Google actually authenticated us. A deliberately
# invalid token returns invalid-argument ONLY after the credentials are accepted;
# a bad key or a wrong project fails earlier, with an auth or permission error.
NODE_PROBE='const a=require("firebase-admin"),s=require("/run/secrets/firebase-service-account.json");a.initializeApp({credential:a.credential.cert(s),projectId:s.project_id});a.messaging().send({token:"deliberately-invalid-token",notification:{title:"t",body:"b"}}).then(()=>console.log("UNEXPECTED_SUCCESS")).catch(e=>console.log(e.code||e.message))'
cred=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
         exec -T rest-api node -e "$NODE_PROBE" 2>&1)
case "$cred" in
  *invalid-argument*)
    ok "FCM credentials authenticate (Google rejected a deliberately invalid token, which it can only do after accepting the key)" ;;
  *UNEXPECTED_SUCCESS*)
    bad "FCM accepted a token that cannot exist — this probe is not testing what it claims" ;;
  *)
    bad "FCM credential probe failed to authenticate: $cred" ;;
esac

# -------------------------------------------------------------------------
printf '\n\033[1m================ %d passed, %d failed ================\033[0m\n' "$pass" "$fail"
if [ "$fail" -gt 0 ]; then
  printf '\nFailed checks:\n'
  for f in "${failures[@]}"; do printf '  - %s\n' "$f"; done
  exit 1
fi
printf '\nAll checks passed.\n'
