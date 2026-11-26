#!/usr/bin/env python3
"""Open a WebSocket against the signalling server and report what happened.

Usage: ws-probe.py <host> <port> <path> [bearer-token]

Prints one line: the HTTP status of the upgrade, then — on 101 — the first text frame
the server sends, which is its `connected` greeting.

Deliberately hand-rolled over a raw socket, with no websocket library. The thing being
tested is the HTTP upgrade and the credential on it, and a client library would hide
exactly that: it would raise on a 401 rather than let us read the status, and it would
build the handshake its own way instead of the way our Android client does.
"""
import json
import socket
import ssl
import sys


def read_until(sock, marker: bytes, limit: int = 65536) -> bytes:
    buffer = b""
    while marker not in buffer and len(buffer) < limit:
        chunk = sock.recv(4096)
        if not chunk:
            break
        buffer += chunk
    return buffer


def read_frame(sock) -> str:
    """One unmasked server-to-client text frame. Enough for the greeting."""
    header = sock.recv(2)
    if len(header) < 2:
        return ""
    opcode = header[0] & 0x0F
    length = header[1] & 0x7F
    if length == 126:
        length = int.from_bytes(sock.recv(2), "big")
    elif length == 127:
        length = int.from_bytes(sock.recv(8), "big")

    payload = b""
    while len(payload) < length:
        chunk = sock.recv(length - len(payload))
        if not chunk:
            break
        payload += chunk
    if opcode != 0x1:  # not text; a ping or close
        return ""
    return payload.decode("utf-8", "replace")


def main() -> int:
    host, port, path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    token = sys.argv[4] if len(sys.argv) > 4 else None

    sock = socket.create_connection((host, port), timeout=15)
    if port == 443:
        sock = ssl.create_default_context().wrap_socket(sock, server_hostname=host)

    # The same handshake OkHttp sends, including the Authorization header our
    # SignalingClient now sets. Sec-WebSocket-Key is a fixed value from RFC 6455's
    # own example; the server echoes an accept hash we do not need to verify here.
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
        "Sec-WebSocket-Version: 13\r\n"
    )
    if token:
        request += f"Authorization: Bearer {token}\r\n"
    request += "\r\n"

    sock.sendall(request.encode())
    head = read_until(sock, b"\r\n\r\n")
    status_line = head.split(b"\r\n", 1)[0].decode("latin-1")
    status = status_line.split(" ")[1] if " " in status_line else "?"
    print(f"STATUS {status}")

    if status == "101":
        frame = read_frame(sock)
        if frame:
            print(f"FRAME {frame}")
            try:
                message = json.loads(frame)
                payload = message.get("payload") or {}
                print(f"TYPE {message.get('type')}")
                print(f"CLIENTID {payload.get('clientId', '')}")
            except json.JSONDecodeError:
                pass

    sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
