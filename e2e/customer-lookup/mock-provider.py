#!/usr/bin/env python3
"""Deterministic local HTTP provider used only by the TPIP E2E acceptance test."""

import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class State:
    lock = threading.Lock()
    total = 0
    requests = []

    @classmethod
    def record(cls, member_no, request_id, authorized):
        with cls.lock:
            cls.total += 1
            cls.requests.append({
                "member_no": member_no,
                "request_id": request_id,
                "authorized": authorized,
            })

    @classmethod
    def snapshot(cls):
        with cls.lock:
            return {"total": cls.total, "requests": list(cls.requests)}

    @classmethod
    def reset(cls):
        with cls.lock:
            cls.total = 0
            cls.requests = []


class Handler(BaseHTTPRequestHandler):
    server_version = "TPIP-E2E-Mock/1.0"

    def log_message(self, message, *args):
        print("mock-provider", message % args, flush=True)

    def json_response(self, status, body):
        content = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def do_GET(self):
        if self.path == "/mock/health":
            self.json_response(200, {"status": "UP"})
        elif self.path == "/mock/admin/stats":
            self.json_response(200, State.snapshot())
        else:
            self.json_response(404, {"code": "NOT_FOUND"})

    def do_POST(self):
        if self.path == "/mock/admin/reset":
            State.reset()
            self.json_response(200, {"status": "RESET"})
            return
        if self.path != "/vendor/v1/members/query":
            self.json_response(404, {"code": "NOT_FOUND"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            request = json.loads(self.rfile.read(length) or b"{}")
        except (ValueError, json.JSONDecodeError):
            self.json_response(400, {"code": "INVALID_JSON"})
            return
        member_no = request.get("member_no")
        request_id = self.headers.get("X-Request-Id")
        authorized = self.headers.get("X-API-Key") == "ApiKey local-e2e-key"
        State.record(member_no, request_id, authorized)
        if not authorized:
            self.json_response(401, {"code": "UNAUTHORIZED"})
        elif member_no == "C404":
            self.json_response(404, {"code": "MEMBER_NOT_FOUND"})
        elif member_no == "C500":
            self.json_response(503, {"code": "PROVIDER_UNAVAILABLE"})
        elif member_no == "CSLOW":
            time.sleep(4)
            self.json_response(200, self.success(member_no))
        elif member_no == "CBAD":
            self.json_response(200, {"code": "0", "data": {"member_no": member_no}})
        else:
            self.json_response(200, self.success(member_no))

    @staticmethod
    def success(member_no):
        return {
            "code": "0",
            "data": {
                "member_no": member_no,
                "member_name": "张三",
                "mobile_no": "13800138000",
                "member_status": "ACTIVE",
            },
        }


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 19090), Handler).serve_forever()
