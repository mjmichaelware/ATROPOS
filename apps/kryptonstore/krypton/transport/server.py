"""Transport Layer: HTTP endpoints for cluster peering and client ops."""
import json
from http.server import BaseHTTPRequestHandler
from typing import Any, Callable, Dict, Optional

class KryptonHttpHandler(BaseHTTPRequestHandler):
    dispatcher: Optional[Callable[[str, str, Dict[str, Any]], Dict[str, Any]]] = None

    def _send_json(self, status: int, data: Dict[str, Any]) -> None:
        p = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(p)))
        self.end_headers()
        self.wfile.write(p)

    def do_GET(self) -> None:
        resp = self.dispatcher("GET", self.path, {}) if self.dispatcher else {"status": "ok"}
        self._send_json(200, resp)

    def do_POST(self) -> None:
        l = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(l) if l > 0 else b"{}"
        data = json.loads(body.decode("utf-8")) if body else {}
        resp = self.dispatcher("POST", self.path, data) if self.dispatcher else {"status": "accepted"}
        self._send_json(200, resp)
