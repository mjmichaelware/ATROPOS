"""Running the legacy API over a socket.

The blocking server loop, kept apart from the dispatcher it serves so that
reading how a request is routed does not mean reading how a port is opened.
"""

from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
def serve(api, host: str, port: int) -> None:
    api = self

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            api._handle()

        def do_POST(self) -> None:
            api._handle()

        def _handle(self) -> None:
            length = int(
                api.headers.get(
                    "content-length",
                    "0",
                )
            )

            payload: dict[str, object] = {}

            if length:
                try:
                    parsed = json.loads(
                        api.rfile.read(
                            length
                        ).decode("utf-8")
                    )
                except (
                    UnicodeDecodeError,
                    json.JSONDecodeError,
                ):
                    api._send(
                        400,
                        {
                            "error": "INVALID_JSON",
                            "message": (
                                "body must be valid JSON"
                            ),
                        },
                    )
                    return

                if not isinstance(parsed, dict):
                    api._send(
                        400,
                        {
                            "error": "INVALID_JSON",
                            "message": (
                                "body must be a JSON object"
                            ),
                        },
                    )
                    return

                payload = parsed

            status, response = api.dispatch(
                api.command,
                api.path,
                payload,
            )

            api._send(status, response)

        def _send(
            self,
            status: int,
            payload: dict[str, object],
        ) -> None:
            encoded = json.dumps(
                payload,
                indent=2,
                sort_keys=True,
            ).encode("utf-8")

            api.send_response(status)
            api.send_header(
                "content-type",
                "application/json; charset=utf-8",
            )
            api.send_header(
                "content-length",
                str(len(encoded)),
            )
            api.end_headers()
            api.wfile.write(encoded)

    server = ThreadingHTTPServer(
        (host, port),
        Handler,
    )

    print(
        "SpecGraph Foundry listening on "
        f"http://{host}:{port}"
    )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping SpecGraph Foundry.")
    finally:
        server.server_close()
