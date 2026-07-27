import unittest

from specgraph_foundry.http_api.resource_limits import (
    ResourceLimitSettings,
)
from specgraph_foundry.http_api.security import (
    SecurityRejection,
    SecuritySettings,
    security_headers,
    validate_content_type,
    validate_headers,
    validate_host,
    validate_request_target,
)


class SecurityHardeningTest(unittest.TestCase):
    def test_host_allowlist_is_exact_and_safe_for_ports(self) -> None:
        settings = SecuritySettings(allowed_hosts=("127.0.0.1", "localhost"))
        validate_host("127.0.0.1:8787", settings)
        validate_host("localhost", settings)
        with self.assertRaises(SecurityRejection) as context:
            validate_host("evil.example", settings)
        self.assertEqual(context.exception.status, 400)

    def test_request_target_rejections(self) -> None:
        settings = ResourceLimitSettings(max_request_target_bytes=64)
        validate_request_target("/v1/projects", settings)
        bad_targets = [
            "/v1/%",
            "/v1\\projects",
            "/v1/projects#fragment",
            "http://evil.example/v1/projects",
            "/" + ("a" * 100),
        ]
        for target in bad_targets:
            with self.subTest(target=target):
                with self.assertRaises(SecurityRejection):
                    validate_request_target(target, settings)

    def test_header_count_and_byte_limits(self) -> None:
        settings = ResourceLimitSettings(max_header_count=1, max_header_bytes=128)
        validate_headers([("host", "localhost")], settings)
        with self.assertRaises(SecurityRejection) as context:
            validate_headers([("host", "localhost"), ("x-a", "b")], settings)
        self.assertEqual(context.exception.code, "HEADERS_TOO_LARGE")

        byte_settings = ResourceLimitSettings(max_header_count=5, max_header_bytes=128)
        with self.assertRaises(SecurityRejection):
            validate_headers([("x-long", "a" * 200)], byte_settings)

    def test_content_type_enforcement(self) -> None:
        validate_content_type("GET", 10, None)
        validate_content_type("POST", 10, "application/json; charset=utf-8")
        with self.assertRaises(SecurityRejection) as context:
            validate_content_type("POST", 10, "text/plain")
        self.assertEqual(context.exception.status, 415)

    def test_security_headers_are_json_api_safe(self) -> None:
        headers = security_headers()
        self.assertEqual(headers["x-content-type-options"], "nosniff")
        self.assertEqual(headers["referrer-policy"], "no-referrer")
        self.assertIn("default-src 'none'", headers["content-security-policy"])
        self.assertIn("geolocation=()", headers["permissions-policy"])
        self.assertNotIn("strict-transport-security", headers)

    def test_errors_do_not_reflect_hostile_input(self) -> None:
        settings = ResourceLimitSettings()
        hostile = "/v1/%ZZ?<script>alert(1)</script>"
        try:
            validate_request_target(hostile, settings)
        except SecurityRejection as error:
            self.assertNotIn("<script>", str(error))
            self.assertNotIn("%ZZ", str(error))


if __name__ == "__main__":
    unittest.main()
