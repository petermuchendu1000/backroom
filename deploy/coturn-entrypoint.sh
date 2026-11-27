#!/bin/bash
# coturn entrypoint: enable TURNS when a certificate is available, and restart
# turnserver when that certificate is renewed.
#
# Runs as nobody (uid 65534) inside the coturn container. Every argument given
# to this script is passed through to turnserver; --cert and --pkey are appended
# when a usable pair is present in $CERT_DIR.
#
# Two facts shape this script:
#
#   1. coturn has no reload. `turnserver -h` on 4.19.2 documents no SIGHUP and no
#      way to re-read certificates, so a renewal means restarting the process.
#      Restarting drops in-flight relay sessions. With a ~60-day Let's Encrypt
#      renewal cycle that is a few seconds of disruption every other month, and
#      there is no way to avoid it short of patching coturn.
#
#   2. TURNS is an enhancement; plain TURN on 3478 is the critical path. If no
#      certificate ever appears, this starts turnserver anyway rather than
#      leaving the service down — but says so loudly on every poll, because a
#      silent downgrade is how you discover six months later that no client on a
#      UDP-blocking network has ever been able to place a call.

set -u

CERT_DIR="${CERT_DIR:-/certs}"
CRT="$CERT_DIR/turn.crt"
KEY="$CERT_DIR/turn.key"
WAIT_FOR_CERT="${WAIT_FOR_CERT:-90}"   # seconds to wait at startup
POLL="${POLL:-60}"                     # seconds between renewal checks

log() { printf '%s coturn-entrypoint: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

child=""

shutdown() {
  log "received shutdown signal; stopping turnserver"
  if [ -n "$child" ] && kill -0 "$child" 2>/dev/null; then
    kill -TERM "$child" 2>/dev/null
    wait "$child" 2>/dev/null
  fi
  exit 0
}
trap shutdown TERM INT

# A pair is usable only if both files exist, are non-empty, and are READABLE BY
# THIS PROCESS. The readability check is the point: Caddy writes certificates
# root-owned mode 600, so a pair can be present and still be unusable, and
# coturn's own error message for that case names no file and is easy to misread
# as a missing certificate.
cert_usable() {
  [ -s "$CRT" ] && [ -s "$KEY" ] && [ -r "$CRT" ] && [ -r "$KEY" ]
}

cert_fingerprint() {
  cat "$CRT" "$KEY" 2>/dev/null | sha256sum | cut -d' ' -f1
}

diagnose_unusable() {
  if [ ! -e "$CRT" ]; then
    log "  $CRT does not exist — is the cert-sync service running?"
  elif [ ! -r "$CRT" ]; then
    log "  $CRT exists but is NOT READABLE by uid $(id -u) (mode $(stat -c %a "$CRT" 2>/dev/null), owner $(stat -c %u:%g "$CRT" 2>/dev/null))"
  elif [ ! -s "$CRT" ]; then
    log "  $CRT is empty"
  fi
  if [ ! -e "$KEY" ]; then
    log "  $KEY does not exist"
  elif [ ! -r "$KEY" ]; then
    log "  $KEY exists but is NOT READABLE by uid $(id -u) (mode $(stat -c %a "$KEY" 2>/dev/null), owner $(stat -c %u:%g "$KEY" 2>/dev/null))"
  fi
}

# ---------------------------------------------------------------- startup wait
waited=0
while ! cert_usable && [ "$waited" -lt "$WAIT_FOR_CERT" ]; do
  if [ "$waited" -eq 0 ]; then
    log "waiting up to ${WAIT_FOR_CERT}s for a usable certificate pair in $CERT_DIR"
  fi
  sleep 5
  waited=$((waited + 5))
done

tls_args=()
if cert_usable; then
  tls_args=(--cert "$CRT" --pkey "$KEY")
  expiry=$(openssl x509 -in "$CRT" -noout -enddate 2>/dev/null | cut -d= -f2)
  log "TURNS ENABLED on the TLS listener — certificate expires $expiry"
else
  log "================================================================"
  log "TURNS IS DISABLED: no usable certificate after ${WAIT_FOR_CERT}s."
  log "Plain TURN on 3478 will work. Clients on networks that block UDP"
  log "or require TLS will NOT be able to relay."
  diagnose_unusable
  log "================================================================"
fi

start_turnserver() {
  turnserver "$@" "${tls_args[@]}" &
  child=$!
  log "turnserver started (pid $child)${tls_args:+ with TURNS}"
}

start_turnserver "$@"
known=$(cert_usable && cert_fingerprint || printf 'none')

# ------------------------------------------------------- renewal / crash watch
while :; do
  sleep "$POLL" &
  wait $! 2>/dev/null

  # If turnserver died, exit so Docker's restart policy handles it rather than
  # leaving a container that is "up" with nothing listening.
  if ! kill -0 "$child" 2>/dev/null; then
    wait "$child" 2>/dev/null
    rc=$?
    log "turnserver exited (status $rc); exiting so the container restarts"
    exit "$rc"
  fi

  current=$(cert_usable && cert_fingerprint || printf 'none')

  if [ "$current" != "$known" ]; then
    if [ "$current" = "none" ]; then
      log "certificate became unusable — leaving turnserver running on the old one"
      diagnose_unusable
      known="$current"
      continue
    fi
    log "certificate changed; restarting turnserver to load it"
    log "in-flight relay sessions will drop — coturn cannot reload certificates"
    kill -TERM "$child" 2>/dev/null
    wait "$child" 2>/dev/null
    tls_args=(--cert "$CRT" --pkey "$KEY")
    start_turnserver "$@"
    known="$current"
  fi
done
