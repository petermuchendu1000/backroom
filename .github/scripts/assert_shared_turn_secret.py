#!/usr/bin/env python3
"""coturn, the REST API and the signalling service must share one TURN secret name.

All three compute the same HMAC-SHA1 over the same username. coturn recomputes it to
decide whether to allow an allocation, so if any of them is reading a different
environment variable — or a different value — every relayed call fails. Nothing logs
a useful error: coturn sees a wrong password, the client sees a call that will not
connect.

This repository shipped exactly that. Three names and three values at once:

    turnserver.conf                         static-auth-secret=CHANGE_ME_IN_PRODUCTION...
    rest-api  src/config/configuration.ts   TURN_LONG_TERM_SECRET
    ws-signaling  cmd/server/main.go        TURN_SECRET  ("backroom_turn_secret")

so the signalling service handed every client a credential coturn would never accept.

Three things are checked:

  1. Both services read TURN_STATIC_AUTH_SECRET, and nothing reads an older name.
  2. docker-compose passes that same variable to coturn, the API and the signalling
     service.
  3. turnserver.conf contains no static-auth-secret value. A committed TURN secret is
     an open relay: anyone who reads the repository can mint credentials and push
     their traffic through the server, billed to whoever runs it.

Exits non-zero, naming what is wrong.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CANONICAL = "TURN_STATIC_AUTH_SECRET"
RETIRED = ("TURN_LONG_TERM_SECRET", "TURN_SECRET", "TURN_CREDENTIAL", "TURN_USERNAME")

READERS = [
    ("rest-api config", "backend/rest-api/src/config/configuration.ts"),
    ("ws-signaling main.go", "backend/ws-signaling/cmd/server/main.go"),
]
COMPOSE = "docker-compose.yml"
COTURN_CONF = "backend/coturn/turnserver.conf"


def main() -> int:
    problems: list[str] = []

    for label, relative in READERS:
        path = ROOT / relative
        if not path.is_file():
            problems.append(f"missing file: {relative}")
            continue
        text = path.read_text()

        if CANONICAL not in text:
            problems.append(f"{relative}: does not read {CANONICAL}")
        else:
            print(f"  {label:<24} reads {CANONICAL}")

        for retired in RETIRED:
            # TURN_SECRET is a substring of nothing else here, but TURN_CREDENTIAL_TTL
            # is a legitimate separate setting, so match whole names.
            if re.search(rf"\b{retired}\b(?!_TTL)", text):
                problems.append(
                    f"{relative}: still references the retired name {retired}. "
                    f"Every component must read {CANONICAL}."
                )

    compose_path = ROOT / COMPOSE
    if not compose_path.is_file():
        problems.append(f"missing file: {COMPOSE}")
    else:
        compose = compose_path.read_text()
        uses = len(re.findall(rf"\{{{CANONICAL}[:?}}]", compose))
        if uses < 3:
            problems.append(
                f"{COMPOSE}: {CANONICAL} is referenced {uses} time(s); coturn, the "
                "REST API and the signalling service all need it."
            )
        else:
            print(f"  {'docker-compose':<24} passes {CANONICAL} to {uses} service(s)")
        for retired in RETIRED:
            if re.search(rf"\b{retired}\b(?!_TTL)", compose):
                problems.append(f"{COMPOSE}: still references the retired name {retired}")

    conf_path = ROOT / COTURN_CONF
    if not conf_path.is_file():
        problems.append(f"missing file: {COTURN_CONF}")
    else:
        for number, line in enumerate(conf_path.read_text().splitlines(), start=1):
            stripped = line.strip()
            if stripped.startswith("#"):
                continue
            if re.match(r"^static-auth-secret\s*=", stripped):
                problems.append(
                    f"{COTURN_CONF}:{number}: a static-auth-secret value is committed. "
                    "Pass it at startup instead; a published TURN secret is an open relay."
                )
            if re.match(r"^user\s*=", stripped):
                problems.append(
                    f"{COTURN_CONF}:{number}: a static user is committed. coturn ignores "
                    "static users in use-auth-secret mode, so this is dead configuration "
                    "that also publishes a password."
                )
        else:
            print(f"  {'turnserver.conf':<24} contains no committed secret")

    if problems:
        print()
        for problem in problems:
            print(f"FAIL: {problem}")
        return 1

    print(f"\nOK: one TURN secret name, nothing committed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
