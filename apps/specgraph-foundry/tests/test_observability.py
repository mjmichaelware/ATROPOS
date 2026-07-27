import io
import json
import unittest

from specgraph_foundry.http_api.observability import (
    Observability,
    parse_traceparent,
    safe_route,
)


class ObservabilityTest(unittest.TestCase):
    def test_json_logs_use_safe_allowlist(self) -> None:
        stream = io.StringIO()
        obs = Observability(stream=stream)
        obs.log(
            "INFO",
            "http_request_complete",
            request_id="request-1",
            method="GET",
            route="/v1/projects/{id}",
            status_code=200,
            duration_ms=3,
            authorization="Bearer token",
            payload="source content",
            error_code="VALIDATION_ERROR",
        )

        line = stream.getvalue().strip()
        payload = json.loads(line)
        self.assertEqual(payload["event"], "http_request_complete")
        self.assertEqual(payload["request_id"], "request-1")
        self.assertEqual(payload["route"], "/v1/projects/{id}")
        self.assertNotIn("authorization", payload)
        self.assertNotIn("payload", payload)
        self.assertNotIn("Bearer token", line)
        self.assertNotIn("source content", line)

    def test_route_templates_do_not_log_raw_identifiers_or_query(self) -> None:
        route = safe_route(
            "/v1/projects/4c1d4e89-3d97-4895-a6da-3a864f7d40ac/documents?cursor=secret"
        )
        self.assertEqual(route, "/v1/projects/{id}/documents")
        self.assertNotIn("4c1d4e89", route)
        self.assertNotIn("cursor", route)

    def test_traceparent_parsing_is_strict(self) -> None:
        parsed = parse_traceparent(
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        )
        self.assertEqual(
            parsed,
            (
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
            ),
        )
        self.assertIsNone(parse_traceparent("malformed"))
        self.assertIsNone(
            parse_traceparent(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01"
            )
        )

    def test_spans_and_metrics_have_bounded_attributes(self) -> None:
        obs = Observability()
        with obs.span(
            "http.request",
            traceparent="00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            attributes={
                "route": "/v1/projects/{id}",
                "owner_id": "not-allowed",
                "dependency": "database",
            },
        ) as span:
            self.assertEqual(span.trace_id, "4bf92f3577b34da6a3ce929d0e0e4736")
        self.assertEqual(obs.spans[0].attributes["route"], "/v1/projects/{id}")
        self.assertNotIn("owner_id", obs.spans[0].attributes)

        obs.metric(
            "http.requests",
            route="/v1/projects/{id}",
            owner_id="attacker",
            status="200",
        )
        metric_key = next(iter(obs.metrics))
        labels = dict(metric_key[1])
        self.assertEqual(labels, {"route": "/v1/projects/{id}", "status": "200"})

    def test_shutdown_is_idempotent_and_nonfatal(self) -> None:
        obs = Observability(enabled=False)
        obs.shutdown()
        obs.shutdown()


if __name__ == "__main__":
    unittest.main()
