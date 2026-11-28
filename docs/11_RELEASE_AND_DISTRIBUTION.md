# Building a signed APK and serving it from ongero.com

Ongero is distributed outside the Play Store for now: people download an APK from
`https://dl.ongero.com/ongero.apk` and install it directly. This document covers making
that APK installable and getting it onto the server.

Two things make this different from the debug builds used during development:

- **A debug build cannot be given to users.** It is signed with the shared Android debug
  key, points at production only when passed `-PprodBackend=true`, and is marked
  debuggable, which means any process on the device can attach to it.
- **An unsigned release build cannot be installed at all.** Android refuses it outright.
  Before the change that introduced this document, `./gradlew assembleRelease` exited 0
  and produced exactly that: a green build and a useless file.

---

## 0. Read this before you create the keystore

**The keystore is the app's identity, and losing it is unrecoverable.**

Android decides whether an update is "the same app" by comparing signatures. An update
signed with a different key is rejected — not with a warning, but as a different
application. Users would have to uninstall, losing their account session and local data,
and if Ongero is ever on the Play Store, Google will refuse the upload.

There is no reset, no support ticket, no recovery. Back it up before you use it.

The same applies to the passwords. A keystore you cannot open is a keystore you have
lost.

---

## 1. Create the keystore

Once, on a machine you control. Not on the server, and not in the repository directory.

```bash
keytool -genkeypair -v \
  -keystore ongero-release.jks \
  -alias ongero \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

`keytool` ships with the JDK; Android Studio installs one, typically reachable as
`"$JAVA_HOME/bin/keytool"`.

It asks for a store password, a key password and a distinguished name. The name fields
are cosmetic for sideloaded distribution — organisation `Ongero`, country `KE` is fine.
`-validity 10000` is about 27 years; a certificate that expires mid-life is its own
unrecoverable problem.

### Back it up now, before building anything

Three copies, two of them not on this laptop. A password manager entry holding both the
file and the two passwords is the simplest arrangement that survives a dead disk.

---

## 2. Point Gradle at it

Create `keystore.properties` in the repository root — it is gitignored, along with
`*.jks` and `*.keystore`:

```properties
storeFile=/absolute/path/to/ongero-release.jks
storePassword=...
keyAlias=ongero
keyPassword=...
```

`app/build.gradle.kts` reads this file, or the environment variables
`ONGERO_KEYSTORE`, `ONGERO_KEYSTORE_PASSWORD`, `ONGERO_KEY_ALIAS` and
`ONGERO_KEY_PASSWORD` if you prefer to keep nothing on disk. If neither is present the
release build still runs, prints a warning, and produces an unsigned APK.

---

## 3. Build

```bash
./gradlew clean assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

A release build always uses the production URLs (`NetworkConfig.kt`), so no
`-PprodBackend` flag is needed or wanted here.

### Confirm it is actually signed

A build that exits 0 is not evidence. Ask the APK:

```bash
"$ANDROID_HOME/build-tools/<version>/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

You want a printed certificate with your DN and `Verifies`. `DOES NOT VERIFY`, or a
complaint that the APK is unsigned, means `keystore.properties` was not picked up — and
the Gradle warning will have said so.

### Check it installs

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

If a debug build of `com.ongero.app` is already on the device, this fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` — different signing key, same package name. That is
the signature check doing its job. Uninstall the debug build first.

---

## 4. DNS, once

An `A` record for `dl.ongero.com` → `95.179.194.39`, **DNS only (grey cloud)**, like
`api`, `ws` and `turn`.

Grey-clouded because Caddy proves control of the name over port 80, and Cloudflare's
proxy would answer that challenge itself.

**Add the record before deploying the Caddy change.** Caddy obtains certificates per
hostname, so a missing record cannot break `api`, `ws` or `turn` — but it will retry for
this name, and Let's Encrypt rate-limits repeated failures per domain.

```bash
dig +short dl.ongero.com
```

Must print `95.179.194.39` and nothing else before you continue.

---

## 5. Deploy the server side

```bash
cd /opt/ongero && git pull
```

```bash
grep -q '^DOWNLOAD_HOST=' .env || printf 'DOWNLOAD_HOST=dl.ongero.com\n' >> .env
```

```bash
cd /opt/ongero && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d caddy
```

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs caddy --tail 30 | grep -i "certificate obtained"
```

A fourth hostname should appear. If instead you see ACME failures, **stop** and check
DNS rather than restarting Caddy repeatedly.

---

## 6. Upload the APK

From your machine, as `ongero.apk` — that exact name is what the website links to and
what `verify.sh` checks:

```powershell
scp app\build\outputs\apk\release\app-release.apk root@95.179.194.39:/opt/ongero/downloads/ongero.apk
```

`downloads/` is bind-mounted read-only into Caddy, so the web tier can serve the file
but cannot replace it.

---

## 7. Verify

```bash
cd /opt/ongero && bash deploy/verify.sh 2>&1 | tail -20
```

Section 10 checks three things, and the third is the one that matters: it fetches the
first two bytes and requires `PK`, the ZIP magic every APK begins with. A 200 response
serving an HTML error page, or a truncated upload, passes the first two checks and fails
that one.

Then download it on a phone over mobile data, not WiFi, and install it. That exercises
the real path including the certificate.

---

## 8. The download page

Link to `https://dl.ongero.com/ongero.apk`, and **explain the warning**. Android shows
"this type of file can harm your device" for every sideloaded APK, and the installer
requires the browser to be granted "install unknown apps". Most people stop at that
screen unless the page tells them in advance what they will see and that it is expected.

---

## Shipping an update

`versionCode` in `app/build.gradle.kts` **must increase** for Android to treat a build as
an update. It is currently `1`. An APK with an equal or lower `versionCode` is rejected
with `INSTALL_FAILED_VERSION_DOWNGRADE`.

`versionName` is the human-readable string and has no bearing on the install — keep it
meaningful, but `versionCode` is the one that must move.

Sideloaded apps do not auto-update. Whatever the mechanism for telling users a new
version exists, it has to be built; until then an install is frozen at whatever was
current when it happened.
