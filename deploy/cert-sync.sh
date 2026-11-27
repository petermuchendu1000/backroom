#!/bin/sh
# Copy Caddy's certificate for the TURN hostname into a volume coturn can read.
#
# Runs as root in the cert-sync service. Why it has to exist:
#
# Caddy writes certificates as root:root mode 600, inside directories mode 700.
# The coturn image runs as nobody (uid 65534). Mounting Caddy's data volume
# straight into coturn therefore does NOT work — coturn cannot even traverse the
# directory, let alone read the key. coturn says so itself when it fails:
#
#   ERROR tls-listening-port 5349 is configured, but the TLS and DTLS listeners
#   are disabled. Set valid 'cert' and 'pkey' files (readable by the turnserver
#   process) to enable TLS/DTLS.
#
# So this copies the pair out, re-owned to 65534 — the cert world-readable, the
# key readable only by coturn's user.
#
# The issuer directory is NOT hardcoded. Caddy's layout is
#   certificates/<issuer>/<domain>/<domain>.crt
# where <issuer> is "acme-v02.api.letsencrypt.org-directory" for production
# Let's Encrypt, something else for staging, and "local" for its internal CA.
# Discovering it with find means switching CA or issuer needs no change here.

set -eu

TURN_HOST="${TURN_HOST:?TURN_HOST must be set}"
CADDY_DATA="${CADDY_DATA:-/caddy-data}"
OUT="${OUT:-/out}"
INTERVAL="${INTERVAL:-60}"
COTURN_UID="${COTURN_UID:-65534}"
COTURN_GID="${COTURN_GID:-65534}"

log() { printf '%s cert-sync: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

mkdir -p "$OUT"
chmod 755 "$OUT"

log "watching $CADDY_DATA for a certificate for $TURN_HOST (every ${INTERVAL}s)"

last=""
announced_missing=0

while :; do
  # -maxdepth keeps this from walking the whole volume. The path shape is
  # fixed; only the issuer segment is unknown.
  crt=$(find "$CADDY_DATA" -maxdepth 5 -type f -name "${TURN_HOST}.crt" 2>/dev/null | head -1)

  if [ -z "$crt" ]; then
    if [ "$announced_missing" -eq 0 ]; then
      log "no certificate for $TURN_HOST yet — waiting for Caddy to obtain one."
      log "if this persists: is there a site block for $TURN_HOST in the Caddyfile,"
      log "does $TURN_HOST resolve to this server, and is port 80 reachable?"
      announced_missing=1
    fi
    sleep "$INTERVAL"
    continue
  fi
  announced_missing=0

  key="${crt%.crt}.key"
  if [ ! -f "$key" ]; then
    log "found $crt but no matching key at $key — skipping this cycle"
    sleep "$INTERVAL"
    continue
  fi

  # Hash both halves: a renewal changes the certificate, and a reissue can
  # change the key too. Either must trigger a copy.
  sum=$(cat "$crt" "$key" | sha256sum | cut -d' ' -f1)

  if [ "$sum" != "$last" ]; then
    # Write to temporary names and move into place, so coturn never reads a
    # half-written file. mv within one filesystem is atomic.
    cp "$crt" "$OUT/.turn.crt.tmp"
    cp "$key" "$OUT/.turn.key.tmp"

    chown "$COTURN_UID:$COTURN_GID" "$OUT/.turn.crt.tmp" "$OUT/.turn.key.tmp"
    chmod 644 "$OUT/.turn.crt.tmp"   # the certificate is public by nature
    chmod 600 "$OUT/.turn.key.tmp"   # the key is readable only by coturn's user

    mv "$OUT/.turn.crt.tmp" "$OUT/turn.crt"
    mv "$OUT/.turn.key.tmp" "$OUT/turn.key"

    # Record the fingerprint and expiry so `docker compose logs cert-sync`
    # answers "which certificate is coturn actually serving?" without anyone
    # having to go digging. Neither value is secret.
    if command -v openssl >/dev/null 2>&1; then
      expiry=$(openssl x509 -in "$OUT/turn.crt" -noout -enddate 2>/dev/null | cut -d= -f2)
      fp=$(openssl x509 -in "$OUT/turn.crt" -noout -fingerprint -sha256 2>/dev/null | cut -d= -f2)
      log "installed certificate for $TURN_HOST (expires $expiry, sha256 $fp)"
    else
      log "installed certificate for $TURN_HOST"
    fi

    # coturn has no reload: `turnserver -h` documents no SIGHUP and no
    # re-read. Its entrypoint watches these files and restarts itself.
    log "coturn will pick this up within its own poll interval and restart"
    last="$sum"
  fi

  sleep "$INTERVAL"
done
