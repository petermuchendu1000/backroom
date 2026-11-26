#!/usr/bin/env python3
"""POST a correctly signed PalPluss webhook at a running server.

Usage: send-palpluss-webhook.py <url> <transaction-id> <amount-kes> <whsec_secret>

Prints only the HTTP status code, so a shell check can compare it.

The signature is built the way PalPluss documents it — base64 HMAC-SHA256 over
"{webhook-id}.{webhook-timestamp}.{raw body}" with the base64-decoded secret — and
deliberately NOT by calling our own verifier. A signer and a verifier that share an
implementation agree with each other whatever they both get wrong.
"""
import base64
import hashlib
import hmac
import json
import sys
import time
import urllib.request

url, transaction_id, amount, secret = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]

body = json.dumps({
    "event": "transaction.updated",
    "event_type": "transaction.success",
    "transaction": {
        "id": transaction_id,
        "type": "STK",
        "status": "SUCCESS",
        "amount": amount,
        "currency": "KES",
        "phone_number": "254712345678",
        "external_reference": "ONGCONTRACT1",
        "provider": "m-pesa",
        "mpesa_receipt": "LGR019G3J2",
        "result_code": "0",
        "result_desc": "The service request is processed successfully.",
    },
}, separators=(",", ":")).encode()

msg_id = "msg_contract_check"
timestamp = str(int(time.time()))
key = base64.b64decode(secret.removeprefix("whsec_"))
signature = base64.b64encode(
    hmac.new(key, f"{msg_id}.{timestamp}.".encode() + body, hashlib.sha256).digest()
).decode()

request = urllib.request.Request(url, data=body, method="POST", headers={
    "Content-Type": "application/json",
    "webhook-id": msg_id,
    "webhook-timestamp": timestamp,
    "webhook-signature": f"v1,{signature}",
})

try:
    with urllib.request.urlopen(request, timeout=20) as response:
        print(response.status)
except urllib.error.HTTPError as e:
    print(e.code)
