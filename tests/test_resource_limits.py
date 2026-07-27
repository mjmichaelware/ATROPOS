import math
import unittest

from specgraph_foundry.http_api.resource_limits import (
    JsonLimitExceeded,
    ResourceLimitSettings,
    validate_json_limits,
)
from specgraph_foundry.http_api.security import (
    RateLimiter,
    SecuritySettings,
)


class ResourceLimitsTest(unittest.TestCase):
    def test_json_depth_limit(self) -> None:
        settings = ResourceLimitSettings(max_json_depth=2)
        with self.assertRaises(JsonLimitExceeded):
            validate_json_limits({"a": {"b": {"c": 1}}}, settings)

    def test_json_item_limit(self) -> None:
        settings = ResourceLimitSettings(max_json_items=3)
        with self.assertRaises(JsonLimitExceeded):
            validate_json_limits({"a": [1, 2, 3]}, settings)

    def test_json_string_and_key_limits(self) -> None:
        settings = ResourceLimitSettings(max_json_string_bytes=4)
        with self.assertRaises(JsonLimitExceeded):
            validate_json_limits({"a": "12345"}, settings)
        with self.assertRaises(JsonLimitExceeded):
            validate_json_limits({"12345": "a"}, settings)

    def test_nonfinite_json_numbers_are_rejected(self) -> None:
        settings = ResourceLimitSettings()
        with self.assertRaises(JsonLimitExceeded):
            validate_json_limits({"value": math.inf}, settings)

    def test_rate_limiter_returns_retry_after_and_expires(self) -> None:
        limiter = RateLimiter(
            SecuritySettings(
                rate_limit_requests=2,
                rate_limit_window_seconds=10,
            )
        )
        self.assertIsNone(limiter.check("owner", now=1.0))
        self.assertIsNone(limiter.check("owner", now=2.0))
        self.assertEqual(limiter.check("owner", now=3.0), 8)
        self.assertIsNone(limiter.check("owner", now=12.0))

    def test_rate_limiter_hashes_keys_and_caps_memory(self) -> None:
        limiter = RateLimiter(
            SecuritySettings(
                rate_limit_requests=10,
                rate_limit_window_seconds=60,
                max_limiter_entries=16,
            )
        )
        for index in range(20):
            limiter.check(f"owner-{index}", now=1.0)
        self.assertLessEqual(len(limiter.entries), 16)
        self.assertTrue(
            all("owner-" not in key for key in limiter.entries)
        )


if __name__ == "__main__":
    unittest.main()
