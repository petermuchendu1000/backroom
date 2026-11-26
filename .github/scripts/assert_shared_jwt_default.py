#!/usr/bin/env python3
"""The REST API and the signalling service must default to the SAME JWT secret.

One service mints the tokens the other verifies. When their defaults differ, every
token is valid and every WebSocket upgrade is still refused with 401 — and nothing
anywhere says why, because both services are behaving exactly as configured.

That is not hypothetical. This repository shipped three different defaults at once:

    backend/rest-api/src/config/configuration.ts  'CHANGE_ME_IN_PRODUCTION'
    docker-compose.yml  (rest-api)                'dev_jwt_secret_change_in_production'
    docker-compose.yml  (ws-signaling)            'dev_jwt_secret_backroom_2026_...'
    backend/ws-signaling/cmd/server/main.go       'dev_jwt_secret_backroom_2026_...'

so `docker compose up` with no .env produced an API whose every token the signalling
server rejected. A comment asking the next person to keep four files in step is not a
mechanism. This is.

Exits non-zero, naming the files that disagree.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# (label, file, regex with one capture group for the default value)
SOURCES = [
    (
        "rest-api config",
        "backend/rest-api/src/config/configuration.ts",
        r"secret:\s*process\.env\.JWT_SECRET\s*\|\|\s*'([^']+)'",
    ),
    (
        "ws-signaling main.go",
        "backend/ws-signaling/cmd/server/main.go",
        r'JWTSecret:\s*getEnv\("JWT_SECRET",\s*"([^"]+)"\)',
    ),
]

# docker-compose declares it once per service, and both must agree too.
COMPOSE = ("docker-compose.yml", r"JWT_SECRET=\$\{JWT_SECRET:-([^}]+)\}")


def main() -> int:
    found: list[tuple[str, str]] = []
    problems: list[str] = []

    for label, relative, pattern in SOURCES:
        path = ROOT / relative
        if not path.is_file():
            problems.append(f"missing file: {relative}")
            continue
        matches = re.findall(pattern, path.read_text())
        if len(matches) != 1:
            problems.append(
                f"{relative}: expected exactly one JWT_SECRET default, found {len(matches)}. "
                "If the code moved, update this script rather than deleting the check."
            )
            continue
        found.append((label, matches[0]))

    compose_path = ROOT / COMPOSE[0]
    if not compose_path.is_file():
        problems.append(f"missing file: {COMPOSE[0]}")
    else:
        compose_defaults = re.findall(COMPOSE[1], compose_path.read_text())
        if not compose_defaults:
            problems.append(f"{COMPOSE[0]}: no JWT_SECRET defaults found")
        for index, value in enumerate(compose_defaults, start=1):
            found.append((f"docker-compose service #{index}", value))

    for label, value in found:
        print(f"  {label:<30} {value}")

    distinct = {value for _, value in found}
    if len(distinct) > 1:
        problems.append(
            "JWT_SECRET defaults disagree: "
            + ", ".join(sorted(repr(v) for v in distinct))
            + ". The REST API mints tokens the signalling service verifies; different "
            "defaults mean every WebSocket upgrade is refused with 401."
        )

    if problems:
        print()
        for problem in problems:
            print(f"FAIL: {problem}")
        return 1

    print(f"\nOK: {len(found)} declaration(s) share one default")
    return 0


if __name__ == "__main__":
    sys.exit(main())
