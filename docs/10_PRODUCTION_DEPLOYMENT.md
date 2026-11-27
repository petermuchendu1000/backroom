# Deploying the Ongero backend to Vultr

A step-by-step guide for **`ongero-backend`** — Vultr, London GB, Debian 12 x64,
2 vCPU / 4 GB / 80 GB SSD, at **95.179.194.39**.

Everything here runs against files committed in this repository. You do not hand-write a
compose file or a proxy config; `docker-compose.prod.yml`, `deploy/Caddyfile` and
`deploy/verify.sh` are the real artefacts and this guide tells you how to use them.

---

## Why this is a dedicated box

An earlier draft of this guide targeted `95.179.194.39`, the instance named
`company-registration`, and opened by insisting on an inventory before touching it.
That inventory is why you are reading a different address now. That server turned out to
be running a live operator console at `gglabs.tukaze.com` — a systemd-managed Caddy on
ports 80 and 443 reverse-proxying a Python app in `/opt/company_registration`, alongside
an Android SDK and Playwright build environment, on 2 vCPU and 3.9 GB.

Deploying here would have collided three ways: Caddy binds 80 and 443 exclusively, Docker
was not installed and installing it writes iptables rules beside the ufw config guarding
that console, and coturn relaying live audio would contend for CPU with someone's
production web app.

So Ongero got its own instance. The lesson worth keeping: **an instance's name is not
evidence of what it runs.** If you ever point this guide at a different server, run the
inventory in *Appendix A* first. It is read-only and it takes a minute.

## One thing to know: London is roughly 7,000 km from Nairobi

This instance is in London (EMEA). Ongero's users are in Kenya. For the REST API that
costs one round trip and nobody notices. For **TURN-relayed audio it is the whole call
quality**: when both parties fall back to the relay, every audio packet goes
Nairobi → London → Nairobi.

| Path | Round trip |
|---|---|
| Nairobi ↔ London | ~150–190 ms *(ESTIMATE — public route data, not measured from this server)* |
| Nairobi ↔ Nairobi/Mombasa | ~10–40 ms *(ESTIMATE)* |

Relayed calls would carry roughly 300–380 ms of mouth-to-ear delay *(ESTIMATE)*, against
the ~150 ms where conversation starts feeling like a radio interview. Direct
peer-to-peer calls are unaffected; only relayed ones pay this.

London was chosen deliberately, with Johannesburg available in the Vultr console and not
taken. That is a defensible call — London is well connected and already understood — but
it was made **without measuring**, so the figures above remain estimates rather than
facts about this server.

This is reversible and does not block anything. `TURN_HOST` is a single environment
variable: coturn can move to its own box in a nearer region later without redeploying the
API, the database or the signalling service. The API and database are latency-tolerant and
would stay here regardless.

If relayed call quality ever becomes a complaint, measure before moving anything. From a
Kenyan connection, against this server and a Johannesburg candidate:

```bash
mtr --report --report-cycles 20 --tcp --port 443 95.179.194.39
```

That replaces two estimates with a number. Until someone runs it, nobody knows whether
Johannesburg would actually be better — East African routing often still transits Europe
even when a nearer datacentre exists.

---

## What you are deploying

Seven containers, from `docker-compose.yml` plus the `docker-compose.prod.yml` overlay:

| Service | Image / build | Role |
|---|---|---|
| `caddy` | `caddy:2-alpine` | TLS termination, reverse proxy, automatic certificates |
| `rest-api` | built from `backend/rest-api` | NestJS API, prefix `/api/v1` |
| `ws-signaling` | built from `backend/ws-signaling` | Go WebSocket signalling |
| `coturn` | `coturn/coturn@sha256:ccdbc51e…` (4.19.2) | TURN/STUN relay, **host networking** |
| `cert-sync` | same pinned digest | Copies Caddy's TURN certificate where coturn can read it |
| `postgres` | `postgres:15-alpine` | Database |
| `redis` | `redis:7-alpine` | Cache, presence, pub/sub |

### Why cert-sync exists

coturn needs a certificate to serve TURNS on 5349, cannot obtain one itself, and
has no ACME client — and running a second ACME client beside Caddy would mean two
processes competing for port 80. So Caddy obtains the TURN hostname's certificate
too (there is a site block for it in `deploy/Caddyfile` that serves nothing).

coturn cannot read it directly, and this is the part that looks like it should work:

| | |
|---|---|
| Caddy writes certificates | `root:root`, mode **600**, in directories mode **700** |
| coturn runs as | **`nobody`** (uid 65534) |

Mounting `caddy_data` into coturn leaves the file present and unreadable. Verified
by doing it: coturn reports `cannot find certificate file`, `ls` as uid 65534
returns `Permission denied`, and TURNS stays dead. coturn's own error message is
misleading — it says "cannot find" for a file that is right there.

`cert-sync` copies the pair out and re-owns it: certificate `644`, key `600`, both
owned by 65534. It discovers Caddy's issuer directory with `find` rather than
hardcoding `acme-v02.api.letsencrypt.org-directory`, so switching CA or staging
needs no change. `deploy/coturn-entrypoint.sh` then checks the pair is *readable
by its own uid* before passing `--cert`/`--pkey`, and says exactly what is wrong
when it is not:

```
coturn-entrypoint:   /certs/turn.crt exists but is NOT READABLE by uid 65534 (mode 600, owner 0:0)
```

coturn has no reload — `turnserver -h` documents no SIGHUP and no way to re-read
certificates — so the entrypoint also watches the pair and restarts turnserver
when it changes. That drops in-flight relay sessions, which with a ~60-day
renewal cycle is a few seconds every other month and is unavoidable short of
patching coturn. If no certificate ever appears, turnserver starts anyway so
plain TURN on 3478 keeps working, and logs loudly that TURNS is off.

### Why the overlay exists

`docker-compose.yml` on its own is a **development** file. On a box with a public IP it is
actively unsafe. The overlay closes seven specific gaps:

| # | In the dev file | In production |
|---|---|---|
| 1 | Postgres published `5432:5432` — every interface | `127.0.0.1:5432` only |
| 2 | Redis published `6379:6379` | `127.0.0.1:6379` only |
| 3 | coturn behind Docker NAT, 101 relay ports published | host networking, full range |
| 4 | `external-ip` commented out in `turnserver.conf` | `--external-ip=$PUBLIC_IP` |
| 5 | No TLS, no reverse proxy | Caddy on 80/443 |
| 6 | `NODE_ENV=development` | `NODE_ENV=production` |
| 7 | `TURN_URL=turn:coturn:3478` — unroutable from a phone | `turn:turn.ongero.com:3478` |

> **The overlay uses `!override` on every `ports` key, and this is load-bearing.** Compose
> *merges* sequences across files. A narrower binding in the overlay does **not** replace
> the wider one in the base file — without `!override` you get both, and Postgres stays
> publicly bound while looking fixed. Verified: with `!override`, `docker compose config`
> reports exactly one binding per port and nothing public but Caddy's 80 and 443.

### Exposure surface after deployment

Only these should be reachable from the internet:

| Port | Protocol | Service |
|---|---|---|
| 22 | TCP | SSH |
| 80, 443 | TCP | Caddy (80 redirects, and ACME needs it) |
| 3478 | TCP + UDP | STUN/TURN |
| 3479 | TCP + UDP | TURN alternative port |
| 5349 | **TCP only** | TURNS (TURN over TLS) |
| 49152–65535 | UDP | TURN media relay |

Not 5432. Not 6379. Not 8080. Not 8443.

**5349 is TCP only, deliberately.** WebRTC reaches TURNS over TCP+TLS. The UDP half
of that port would be DTLS, and coturn documents DTLS as *"not started by
default"* — it is not enabled here, so opening UDP 5349 would expose a port with
nothing behind it. Verified: a DTLS client against 5349 gets ECONNREFUSED while
the TCP+TLS listener on the same port grants allocations normally.

---

## Step 1 — First login and lock down SSH

```bash
ssh -i ~/.ssh/<your-key> root@95.179.194.39
```

This box is fresh, so there is nothing to inventory — but it has a public IP and is being
scanned for weak SSH already. If you deployed with an SSH key, turn password
authentication off before anything else:

```bash
sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config && sshd -t && systemctl reload ssh
```

`sshd -t` validates the config before the reload, so a typo cannot lock you out. **Keep
this session open** until you have confirmed a second one works.

If you deployed with Vultr's emailed root password instead, add your key first
(`ssh-copy-id -i ~/.ssh/<your-key>.pub root@95.179.194.39` from your laptop) and only
then run the line above.

## Step 2 — DNS

Three A records, all to `95.179.194.39`. The website stays on Cloudflare Pages; these
are new subdomains alongside it.

| Name | Type | Value | Proxy |
|---|---|---|---|
| `api` | A | 95.179.194.39 | **DNS only — grey cloud** |
| `ws` | A | 95.179.194.39 | **DNS only — grey cloud** |
| `turn` | A | 95.179.194.39 | **DNS only — grey cloud** |

**All three must be grey-cloud (unproxied).** This matters more than it looks:

- Cloudflare's proxy **rewrites the `Authorization` header**. We hit this during the
  website deploy — a request with a deliberately invalid token came back `200`. Proxying
  `api.ongero.com` would break every authenticated API call in a way that looks like a
  backend bug.
- Cloudflare does not proxy arbitrary UDP, so a proxied `turn` record cannot carry TURN
  traffic at all.
- Caddy needs to answer the ACME challenge on port 80 directly — **for all three
  names, `turn` included**. Caddy obtains a certificate for the TURN hostname so
  coturn can serve TURNS on 5349; if `turn.ongero.com` does not resolve here, or
  port 80 is unreachable for it, TURNS will not come up and clients on
  UDP-blocking networks will be unable to relay.

Confirm propagation before Step 9, because Caddy will request certificates the moment it
starts and Let's Encrypt rate-limits failures:

```bash
dig +short api.ongero.com ws.ongero.com turn.ongero.com
```

All three must return `95.179.194.39` and nothing else.

## Step 3 — Install Docker

```bash
apt-get update && apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg git ufw iproute2

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable --now docker
docker compose version    # must print v2 or later
```

## Step 4 — Clone the repository

```bash
mkdir -p /opt && cd /opt
git clone https://github.com/petermuchendu1000/backroom.git ongero
cd ongero
git checkout main
```

## Step 5 — Write `.env`

```bash
cp deploy/env.example .env
chmod 600 .env
```

Generate each secret **on the server**:

```bash
echo "POSTGRES_PASSWORD=$(openssl rand -base64 32)"
echo "JWT_SECRET=$(openssl rand -hex 48)"
echo "TURN_STATIC_AUTH_SECRET=$(openssl rand -hex 32)"
```

Paste those into `.env`, then fill in by hand: `ACME_EMAIL`, `PALPLUSS_API_KEY`,
`PALPLUSS_WEBHOOK_SECRET`, `FCM_PROJECT_ID`. `PUBLIC_IP` is already
`95.179.194.39` in the example.

Three notes that cause real outages:

- **`JWT_SECRET` is shared by `rest-api` and `ws-signaling`.** If they disagree, login
  works and every call fails with a 401 on the WebSocket — a symptom that looks like
  anything but a config problem. `deploy/verify.sh` checks this.
- **`TURN_STATIC_AUTH_SECRET` is shared by three components** — coturn, rest-api and
  ws-signaling all compute the same HMAC. A mismatch means calls connect only when both
  parties happen to have a direct peer-to-peer path, so it fails intermittently and
  looks like a network problem.
- **Never reuse a secret you have pasted into a chat window.** Separately: the two
  Cloudflare API tokens from the website deploy are in that conversation's transcript and
  should be rotated.

Compose will refuse to start and name the missing variable rather than falling back to a
development default. That is deliberate.

## Step 6 — Firebase service account

Without this, a listener's phone never rings for an incoming call.

```bash
mkdir -p /opt/ongero/secrets
# Paste the JSON key from the Firebase console:
#   Project settings -> Service accounts -> Generate new private key
nano /opt/ongero/secrets/firebase-service-account.json
chmod 600 /opt/ongero/secrets/firebase-service-account.json
```

The path is mounted read-only into both services at
`/run/secrets/firebase-service-account.json`. `secrets/` is gitignored — confirm with
`git check-ignore -v secrets/firebase-service-account.json` before you continue.

## Step 7 — Firewall

```bash
ufw default deny incoming
ufw default allow outgoing

ufw allow 22/tcp                    # SSH — do this FIRST
ufw allow 80/tcp                    # Caddy + ACME
ufw allow 443/tcp                   # Caddy
ufw allow 3478/tcp                  # STUN/TURN
ufw allow 3478/udp
ufw allow 3479/tcp                  # TURN alternative port
ufw allow 3479/udp
ufw allow 5349/tcp                  # TURNS (TURN over TLS) — TCP only, see above
ufw allow 49152:65535/udp           # TURN media relay

ufw enable
ufw status verbose
```

**The relay range must be the whole 49152–65535.** `backend/coturn/turnserver.conf` sets
`min-port=49152` and `max-port=65535`, and coturn hands clients ports from across that
range. Open only part of it and a predictable fraction of calls fail to carry audio while
signalling looks perfectly healthy. (The dev compose file publishes only
`49152-49252`, which is one of the seven gaps the overlay closes — under host networking
nothing is published and the firewall is the only gate.)

Note that Docker normally writes its own iptables rules and can bypass ufw. It does not
here, because the overlay binds every non-public service to `127.0.0.1` and coturn runs
with host networking. Step 10 verifies this rather than assuming it.

## Step 8 — Build

```bash
cd /opt/ongero
docker compose -f docker-compose.yml -f docker-compose.prod.yml build
```

Expect this to take a while on a small instance — two images from source *(no measured
figure; depends on the plan's CPU)*. Check the merged config before starting anything:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml config | less
```

Confirm by eye: `postgres` and `redis` show only `127.0.0.1` bindings, `coturn` shows
`network_mode: host` with no published ports, and `NODE_ENV` is `production`.

## Step 9 — Start

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

Watch Caddy get its certificates — this is where DNS mistakes surface:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f caddy
```

You want `certificate obtained successfully` for both hosts. If you see ACME failures,
fix DNS and **wait** before retrying; Let's Encrypt rate-limits repeated failures per
domain.

> **One-time-only migrations.** `docker-compose.yml` mounts the three SQL files in
> `backend/migrations/` into `docker-entrypoint-initdb.d`. Postgres runs those **only when
> it initialises an empty data directory** — i.e. the very first start of the
> `postgres_data` volume. If that volume already exists, they are silently skipped and
> you get an empty schema with no error. Step 10 checks for the tables. To apply a
> migration added later, run it explicitly:
>
> ```bash
> docker compose -f docker-compose.yml -f docker-compose.prod.yml \
>   exec -T postgres psql -U ongero -d ongero < backend/migrations/00X_whatever.sql
> ```

## Step 10 — Verify

```bash
cd /opt/ongero
chmod +x deploy/verify.sh
set -a && . ./.env && set +a      # verify.sh reads POSTGRES_USER, PUBLIC_IP, hosts
./deploy/verify.sh
```

It exits non-zero and lists what failed. It checks, in order:

1. All six containers running.
2. **Nothing publicly bound that should not be** — both from inside (`ss`) and by
   attempting a connection from outside. This is the check that catches the Compose
   port-merge trap.
3. HTTPS works, the certificate validates, `http://` redirects, and **`/docs` returns
   404**. Swagger is registered at the root — outside the `api/v1` prefix — and it
   describes every endpoint and DTO on the service; `deploy/Caddyfile` blocks it. That
   block is load-bearing: with it removed, the endpoint serves.
4. A WebSocket upgrade reaches the signalling service through the proxy.
5. `JWT_SECRET` and `TURN_STATIC_AUTH_SECRET` agree across containers, compared by
   SHA-256 prefix so no value is printed, and `JWT_SECRET` is not the development default.
6. **coturn grants a real relay allocation** on 3478 with a freshly minted
   credential, *and refuses both an expired credential and a wrong secret* — so
   the first check is known to be capable of failing.
7. **TURNS works on 5349**, checked two independent ways: the TLS listener
   presents a certificate that verifies against the system trust store, and a
   TURN allocation completes over TCP+TLS.
8. `cert-sync` has installed a certificate, and coturn's logs are **visible to
   `docker logs`** and contain no `Bad configuration format` lines.
9. **coturn is pinned by digest, not by tag**; `coturn` and `cert-sync` resolve to
   the same image; and the running version is the 4.19.2 this configuration was
   verified against.
10. The expected tables exist (the migration trap above).
11. `PALPLUSS_WEBHOOK_SECRET` is set.

Reading `turnutils_uclient` correctly is most of the difficulty, and four separate
traps are each measured against **coturn 4.19.2** with this exact config:

- **The exit code is useless.** It exits non-zero even on a successful allocation,
  because `turnserver.conf` refuses the channel bind to a denied peer range with a
  403 — that list working, not an auth failure.
- **Absence of the error is not success.** An earlier version of this script
  treated "no `Cannot complete Allocation`" as a pass. A connection to a **dead
  port** produced neither the error nor an allocation, and passed. So the script
  requires the positive signal, `Received relay addr`, which only prints with `-v`.
- **`-t` is required for TURNS.** Without it the client uses UDP, and `-S` over UDP
  means DTLS, which coturn does not start by default — so a TURNS check without
  `-t` fails with ECONNREFUSED against a perfectly healthy TLS listener.
- **Every probe needs a unique account id.** `user-quota=12`, allocations live up
  to 600 s, and coturn counts the quota against the id after the colon. Reusing one
  id makes early probes pass and later ones fail with STUN `486`, so running this
  script a few times would report TURNS as broken when it is fine. Measured:
  reused id → `486` and no relay; unique id → 5 relay addresses, three runs of three.

One more thing the logging fix surfaced, which is now a check of its own:
`turnserver.conf` had carried `no-sslv3`, `no-tlsv1` and `no-tlsv1_1` for the sake
of "disabling weak algorithms", and coturn 4.19.2 **rejects all three** —
`WARNING Bad configuration format: no-sslv3`. That section had never done anything,
and `no-stdout-log` is precisely why nobody could see it. They have been removed:
this build reports only TLS 1.2, TLS 1.3 and DTLS 1.2, so TLS 1.2 is already the
floor and the weak versions are not compiled in.

## Step 11 — PalPluss console

Register the callback domain, or webhooks arrive unsigned:

1. Add `api.ongero.com` under **Webhooks**.
2. Set the callback URL to exactly:
   `https://api.ongero.com/api/v1/payments/palpluss/webhook`
3. Copy the `whsec_…` secret into `PALPLUSS_WEBHOOK_SECRET` in `.env`.
4. `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d rest-api`

With no secret set, the service logs a loud warning at boot and accepts callbacks
**without a signature check** — PalPluss delivers to unregistered domains, just unsigned.
Two other defences still apply (the correlation id must be one we issued, and the amount
must match what we asked for), but a signature is the only thing that proves who sent the
message.

## Step 12 — The Android app

No change needed. `app/src/main/java/com/ongero/app/data/network/NetworkConfig.kt` already
points at `https://api.ongero.com/api/v1` and `wss://ws.ongero.com/ws`, which is exactly
what `deploy/Caddyfile` serves. Build a release APK and the endpoints line up.

---

## Operations

### Logs

```bash
cd /opt/ongero
C="-f docker-compose.yml -f docker-compose.prod.yml"
docker compose $C logs -f --tail=100 rest-api
docker compose $C logs -f --tail=100 ws-signaling
docker compose $C logs -f --tail=100 caddy
```

Caddy's access logs are in the `caddy_data` volume at `/data/access-api.log` and
`/data/access-ws.log`, rolled at 10 MiB, five kept.

### Deploying an update

```bash
cd /opt/ongero
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml build
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
./deploy/verify.sh
```

### Rolling back

```bash
cd /opt/ongero
git log --oneline -10
git checkout <previous-commit>
docker compose -f docker-compose.yml -f docker-compose.prod.yml build
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
./deploy/verify.sh
```

Code rolls back cleanly. **A database migration does not** — plan those separately and
take a backup first.

### Backups

```bash
cd /opt/ongero
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec -T postgres pg_dump -U ongero ongero | gzip > "/root/ongero-$(date +%F).sql.gz"
```

A dump on the same disk as the database is not a backup — it dies with the server. Copy it
off, and restore one into a scratch database at least once. An untested backup is a
guess.

Also back up the `caddy_data` volume if you want to avoid re-requesting certificates on a
rebuild; Let's Encrypt's rate limits make this worth a minute.

### Changing the database password

`POSTGRES_PASSWORD` is read **only when Postgres first initialises its data directory**.
Editing `.env` later changes what the API sends, not what the database expects — so the
API starts failing to authenticate. Change both:

```bash
cd /opt/ongero
C="-f docker-compose.yml -f docker-compose.prod.yml"
docker compose $C exec -T postgres psql -U ongero -d ongero \
  -c "ALTER USER ongero WITH PASSWORD 'the-new-one';"
# then update .env to match, and:
docker compose $C up -d rest-api
```

### Stopping

```bash
cd /opt/ongero
C="-f docker-compose.yml -f docker-compose.prod.yml"
docker compose $C down          # keeps volumes
docker compose $C down -v       # DESTROYS the database. There is no undo.
```

---

## Known gaps

Things this deployment does **not** give you. None is a blocker; all are real.

1. **TURNS on 5349 requires port 80 to stay reachable.** It now works — Caddy
   obtains the certificate, `cert-sync` re-owns it, coturn serves it — but that
   chain depends on Caddy renewing, which depends on `turn.ongero.com` resolving
   here and port 80 being open. If renewal silently stops, TURNS degrades about
   60 days later while plain TURN keeps working, so the failure is quiet. The
   `ACME_EMAIL` warnings and `deploy/verify.sh` are what surface it; nothing
   monitors it continuously (see gap 4).
2. **A certificate renewal restarts coturn and drops active calls.** coturn
   documents no SIGHUP and no certificate reload, so `deploy/coturn-entrypoint.sh`
   restarts turnserver when the pair changes. Verified: turnserver restarts and
   serves the new certificate. Anyone mid-call at that moment is disconnected.
   Roughly once every 60 days, unavoidable without patching coturn.
3. **The pinned coturn receives no security updates.** `coturn/coturn` is now
   pinned by digest to `sha256:ccdbc51e…` (coturn **4.19.2**) in *both* places it
   appears — the `coturn` service in `docker-compose.yml` and `cert-sync` in
   `docker-compose.prod.yml`. That buys reproducibility and costs updates: nothing
   will bring in a coturn security fix until somebody changes the digest. Check
   periodically.

   It is pinned in the development file too, on purpose. `turnserver.conf` is
   shared between dev and prod, and it is version-sensitive in ways that fail
   silently — 4.19.2 rejects `no-sslv3`, `no-tlsv1` and `no-tlsv1_1`, which it
   carried for a long time while appearing to harden TLS. Debugging a relay
   problem against a different coturn than production runs is how you reach a
   confident wrong answer.

   To update:

   ```bash
   docker pull coturn/coturn:latest
   docker image inspect coturn/coturn:latest --format '{{index .RepoDigests 0}}'
   ```

   Put that digest in **both** files, then run `./deploy/verify.sh`. It asserts
   the image is pinned rather than tagged, that both services resolve to the same
   image, and that the version is the 4.19.2 this configuration was verified
   against — so a bump that changes behaviour shows up as a failed check instead
   of a silent change. After bumping, re-read `turnserver -h` for the cert and log
   options and check `docker compose logs coturn` for `Bad configuration format`.
4. **London, not East Africa.** See the top of this document.
5. **No monitoring or alerting.** Nothing tells you the API is down except a user.
   `deploy/verify.sh` is a manual check, not a monitor.
6. **No log rotation for the application containers** — only for Caddy's access logs.
   Set Docker's `log-opts` in `/etc/docker/daemon.json` before the disk fills.
7. **Never run on a real Android device.** The client has been built and unit-tested, but
   no call has been placed between two physical phones against this stack.
8. **No live PalPluss transaction has ever been made.** The integration is tested against
   recorded fixtures and a signed-webhook harness. A `pp_test_` key and one real STK push
   would close that gap, and is worth doing before launch rather than after.

## What this guide's verification does and does not establish

`deploy/verify.sh` passing proves the stack is **running, correctly exposed, correctly
routed, and internally consistent on its shared secrets**, and that TURN authentication
accepts a valid credential while refusing an expired one.

It does **not** prove that two people in Kenya can hold a call. That requires two physical
devices on different networks, and no amount of server-side checking substitutes for it.
Treat a green `verify.sh` as a cleared runway, not a completed flight.

---

## Appendix A — Inventory, for pointing this guide at another server

Everything above assumes a dedicated box. If you ever aim this at a server that already
exists, run this **first**. It is read-only. An instance's name tells you nothing about
what it runs — that is how `company-registration` nearly became Ongero's host while
serving a live operator console.

```bash
{ echo "===== HOST ====="; uname -a; cat /etc/os-release | head -3; uptime; echo; echo "===== LISTENING TCP ====="; ss -ltnp 2>&1; echo; echo "===== LISTENING UDP ====="; ss -lunp 2>&1; echo; echo "===== RUNNING SERVICES ====="; systemctl list-units --type=service --state=running --no-pager --no-legend 2>&1 | head -40; echo; echo "===== CADDY / NGINX CONFIG ====="; find /etc/caddy /etc/nginx /opt /srv /root /home -maxdepth 4 \( -iname 'Caddyfile*' -o -iname '*.nginx' \) 2>/dev/null | head; echo; echo "===== DOCKER ====="; docker ps -a 2>&1; docker compose ls 2>&1; echo; echo "===== APP DIRECTORIES ====="; ls -la /opt /srv /var/www 2>&1 | head -40; echo; echo "===== CRON ====="; crontab -l 2>&1; ls -la /etc/cron.d/ 2>&1 | head; echo; echo "===== FIREWALL ====="; ufw status verbose 2>&1; echo; echo "===== RESOURCES ====="; df -h / 2>&1; free -m 2>&1; nproc; } 2>&1 | tee /root/pre-ongero-inventory.txt
```

Read it before continuing. The questions it answers:

- **Is anything holding 80, 443, 3478, 5349, or UDP above 49152?** Caddy binds 80 and 443
  exclusively; coturn under host networking takes 3478, 5349 and the relay range.
- **What hostnames does an existing reverse proxy serve?** That identifies the project
  faster than any directory listing.
- **Is Docker installed?** Installing it on a live box writes iptables rules beside
  whatever firewall is already protecting that service.
- **Disk, RAM and core count.** Two images build from source here, and coturn relays live
  media.

If the box is in use, the honest options are a separate instance, or merging Ongero's
hostnames into the existing reverse proxy and dropping the `caddy` service from the
overlay. The second couples two products' uptime together — choose it deliberately, not
by accident.
