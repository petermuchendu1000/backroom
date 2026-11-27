# Deploying the Ongero backend to Vultr

A step-by-step guide for the existing server at **95.179.203.236** (Vultr, London GB,
Debian 12 x64, instance `company-registration`).

Everything here runs against files committed in this repository. You do not hand-write a
compose file or a proxy config; `docker-compose.prod.yml`, `deploy/Caddyfile` and
`deploy/verify.sh` are the real artefacts and this guide tells you how to use them.

---

## Two things to settle before you touch the server

### 1. That instance is named `company-registration`

The Vultr console shows this server as `company-registration`, which is not a name
anybody picks for an Ongero backend. Either it was reused, or something else is already
running on it. **Step 1 below is an inventory, and it is not optional.** Deploying onto a
box that is already serving something will take that something down: Caddy binds ports 80
and 443 exclusively, and coturn under host networking binds 3478 and a large UDP range.

If it turns out the server is in use, the honest options are a second Vultr instance or a
deliberate decision to retire what is there. Do not find this out by watching it break.

### 2. London is roughly 7,000 km from Nairobi

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

Two reasonable responses:

- **Deploy here now, and plan to move TURN.** The API and database are latency-tolerant.
  coturn is the piece that cares, and `TURN_HOST` is one environment variable — you can
  point it at an African-region relay later without redeploying anything else. Vultr has
  no Kenyan region; Johannesburg is the closest *(VERIFY in the Vultr console before
  relying on it)*.
- **Measure before deciding.** `mtr --report --tcp --port 443 95.179.203.236` from a
  Kenyan connection gives you a real number instead of my estimate. Do this; it is five
  minutes and it replaces two guesses with a fact.

I would deploy here and treat relay placement as a known follow-up. It is reversible and
it does not block anything else.

---

## What you are deploying

Six containers, from `docker-compose.yml` plus the `docker-compose.prod.yml` overlay:

| Service | Image / build | Role |
|---|---|---|
| `caddy` | `caddy:2-alpine` | TLS termination, reverse proxy, automatic certificates |
| `rest-api` | built from `backend/rest-api` | NestJS API, prefix `/api/v1` |
| `ws-signaling` | built from `backend/ws-signaling` | Go WebSocket signalling |
| `coturn` | `coturn/coturn:latest` | TURN/STUN relay, **host networking** |
| `postgres` | `postgres:15-alpine` | Database |
| `redis` | `redis:7-alpine` | Cache, presence, pub/sub |

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
| 49152–65535 | UDP | TURN media relay |

Not 5432. Not 6379. Not 8080. Not 8443. Not 5349 — see *Known gaps*.

---

## Step 1 — Inventory the server before changing anything

```bash
ssh root@95.179.203.236
```

```bash
# What is running, and what owns the ports Ongero needs?
systemctl list-units --type=service --state=running
ss -ltnp
ss -lunp
docker ps -a 2>/dev/null || echo "docker not installed"
ls -la /opt /srv /var/www /home 2>/dev/null
crontab -l 2>/dev/null; ls -la /etc/cron.d/ 2>/dev/null
```

Read the output before continuing. Specifically: **is anything holding 80, 443, 3478, or
a UDP port above 49152?** If yes, stop and decide what happens to it. Also check
`df -h` and `free -m` — the build needs a few GB free.

Capture the inventory somewhere you can refer back to:

```bash
{ date -u; echo '--- services'; systemctl list-units --type=service --state=running; \
  echo '--- tcp'; ss -ltnp; echo '--- udp'; ss -lunp; echo '--- disk'; df -h; } \
  > /root/pre-ongero-inventory.txt
```

## Step 2 — DNS

Three A records, all to `95.179.203.236`. The website stays on Cloudflare Pages; these
are new subdomains alongside it.

| Name | Type | Value | Proxy |
|---|---|---|---|
| `api` | A | 95.179.203.236 | **DNS only — grey cloud** |
| `ws` | A | 95.179.203.236 | **DNS only — grey cloud** |
| `turn` | A | 95.179.203.236 | **DNS only — grey cloud** |

**All three must be grey-cloud (unproxied).** This matters more than it looks:

- Cloudflare's proxy **rewrites the `Authorization` header**. We hit this during the
  website deploy — a request with a deliberately invalid token came back `200`. Proxying
  `api.ongero.com` would break every authenticated API call in a way that looks like a
  backend bug.
- Cloudflare does not proxy arbitrary UDP, so a proxied `turn` record cannot carry TURN
  traffic at all.
- Caddy needs to answer the ACME challenge on port 80 directly.

Confirm propagation before Step 9, because Caddy will request certificates the moment it
starts and Let's Encrypt rate-limits failures:

```bash
dig +short api.ongero.com ws.ongero.com turn.ongero.com
```

All three must return `95.179.203.236` and nothing else.

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
`95.179.203.236` in the example.

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
6. **coturn grants a real relay allocation** with a freshly minted credential, *and
   refuses an expired one* — so the first check is known to be capable of failing.
7. The expected tables exist (the migration trap above).
8. `PALPLUSS_WEBHOOK_SECRET` is set.

On the TURN check: `turnutils_uclient` exits non-zero even on a **successful** allocation
when the channel bind is refused, and `turnserver.conf` denies relaying to private and
loopback ranges on purpose. The script therefore reads the allocation result, not the exit
code. Measured against **coturn 4.19.2** with this exact config: a wrong secret and an
expired credential both produce `Cannot complete Allocation`; a valid credential gets an
allocation and then a `403` on channel bind when the peer is a denied address. A secret
differing by one character is refused.

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

1. **TURNS (TLS-wrapped TURN) does not work.** `turnserver.conf` sets
   `tls-listening-port=5349` but contains no `cert=` or `pkey=` lines, so coturn will
   listen there with no certificate. Do not open 5349. Clients on networks that block
   plain UDP will fail to relay. Fixing it means pointing coturn at Caddy's certificate
   for `turn.ongero.com` and adding the two config lines.
2. **coturn's logs go nowhere useful.** `turnserver.conf` sets both `verbose` and
   `no-stdout-log` with no `log-file`, so output lands inside the container rather than in
   `docker logs`. Debugging a relay problem will be blind until that is changed.
3. **`coturn/coturn:latest` is unpinned, and it has already drifted.** The tag currently
   resolves to **4.19.2**, while the comment in `backend/coturn/turnserver.conf` records a
   behaviour check made against **4.6.1** — so that note is about a different TURN server
   than the one that will run. A rebuild can change the version again with no signal. Pin
   it:

   ```yaml
   coturn:
     image: coturn/coturn@sha256:ccdbc51e6f072130b0fc9eebda21f8d026d0a25581746c52557e887818011a3f
   ```

   (That digest is 4.19.2 as of this writing — re-resolve it rather than trusting this
   line if time has passed.)
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
