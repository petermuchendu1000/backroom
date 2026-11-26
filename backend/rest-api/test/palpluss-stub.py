#!/usr/bin/env python3
"""A stand-in for api.palpluss.com, for the Android contract check.

Answers POST /v1/payments/stk with the envelope PalPluss's OpenAPI document describes,
and GET /v1/transactions/{id} with a pending transaction. Nothing here moves money and
nothing leaves the machine.

It also asserts the two things our gateway could get wrong on the way out:
  - Authorization must be Basic base64(key + ":"), not the bare key. PalPluss's own
    OpenAPI prose says the latter; its guide and every code sample say the former.
  - accountReference and transactionDesc must be within 12 and 13 characters, which
    the real API enforces as maxLength.
A violation returns 400 with a code, so the check fails loudly instead of passing
against a stub that is more forgiving than the real thing.
"""
import base64
import json
import sys
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8097
TRANSACTIONS = {}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass  # the check's own output is the signal

    def _send(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _error(self, code, message, error_code):
        self._send(code, {
            "success": False,
            "error": {"message": message, "code": error_code, "details": {}},
            "requestId": str(uuid.uuid4()),
        })

    def _check_auth(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Basic "):
            self._error(401, "Missing Basic credentials", "INVALID_API_KEY")
            return False
        try:
            decoded = base64.b64decode(header[len("Basic "):]).decode()
        except Exception:
            self._error(401, "Credentials are not base64", "INVALID_API_KEY")
            return False
        # base64(key + ":") decodes to "key:". A bare key would not contain the colon.
        if not decoded.endswith(":") or len(decoded) < 2:
            self._error(401, "Expected base64(API_KEY + ':')", "INVALID_API_KEY")
            return False
        return True

    def do_POST(self):
        if self.path.rstrip("/") != "/v1/payments/stk":
            self._error(404, "Cannot POST " + self.path, "HTTP_404")
            return
        if not self._check_auth():
            return

        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")

        for field in ("amount", "phone", "accountReference", "transactionDesc", "callbackUrl"):
            if field not in body:
                self._error(400, f"{field} is required", "VALIDATION_ERROR")
                return
        if len(body["accountReference"]) > 12:
            self._error(400, "accountReference must be at most 12 characters", "VALIDATION_ERROR")
            return
        if len(body["transactionDesc"]) > 13:
            self._error(400, "transactionDesc must be at most 13 characters", "VALIDATION_ERROR")
            return
        if not isinstance(body["amount"], (int, float)) or body["amount"] < 1:
            self._error(400, "amount must be at least 1", "VALIDATION_ERROR")
            return

        transaction_id = str(uuid.uuid4())
        TRANSACTIONS[transaction_id] = {
            "transactionId": transaction_id,
            "status": "PENDING",
            "amount": body["amount"],
            "resultCode": None,
            "resultDesc": None,
        }
        self._send(200, {
            "success": True,
            "data": {
                "transactionId": transaction_id,
                "tenantId": str(uuid.uuid4()),
                "channelId": None,
                "type": "STK",
                "status": "PENDING",
                "amount": body["amount"],
                "currency": "KES",
                "phone": body["phone"],
                "accountReference": body["accountReference"],
                "transactionDesc": body["transactionDesc"],
                "providerRequestId": "29115-34620561-1",
                "providerCheckoutId": "ws_CO_191220191020363925",
                "transactionFee": 3,
                "resultCode": "0",
                "resultDescription": "Success. Request accepted for processing",
                "createdAt": "2026-11-26T00:00:00.000Z",
                "updatedAt": "2026-11-26T00:00:00.000Z",
            },
            "requestId": str(uuid.uuid4()),
        })

    def do_GET(self):
        if not self.path.startswith("/v1/transactions/"):
            self._error(404, "Cannot GET " + self.path, "HTTP_404")
            return
        if not self._check_auth():
            return
        transaction_id = self.path.rsplit("/", 1)[-1]
        tx = TRANSACTIONS.get(transaction_id)
        if tx is None:
            self._error(404, "Transaction not found", "NOT_FOUND")
            return
        self._send(200, {"success": True, "data": tx, "requestId": str(uuid.uuid4())})


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
