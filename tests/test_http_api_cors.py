import unittest

from specgraph_foundry.http_api.server import is_origin_allowed


class OriginAllowlistTest(unittest.TestCase):
    def test_exact_configured_origin_is_allowed(self):
        self.assertTrue(
            is_origin_allowed(
                "https://specgraph-foundry.vercel.app",
                {"https://specgraph-foundry.vercel.app"},
            )
        )

    def test_per_deployment_vercel_url_is_allowed(self):
        self.assertTrue(
            is_origin_allowed(
                "https://specgraph-foundry-pw5053xe9-mjmichaelwares-projects.vercel.app",
                set(),
            )
        )

    def test_different_vercel_project_is_rejected(self):
        self.assertFalse(
            is_origin_allowed(
                "https://some-other-app-abc123-someone-elses-projects.vercel.app",
                set(),
            )
        )

    def test_unrelated_origin_is_rejected(self):
        self.assertFalse(
            is_origin_allowed(
                "https://evil.example.com",
                {"https://specgraph-foundry.vercel.app"},
            )
        )


if __name__ == "__main__":
    unittest.main()
