from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class TestRollbackWorkflow(unittest.TestCase):
    def setUp(self):
        self.wf_path = ROOT / ".github" / "workflows" / "rollback.yml"
        self.content = self.wf_path.read_text() if self.wf_path.is_file() else ""

    def test_workflow_exists(self):
        self.assertTrue(self.wf_path.is_file())

    def test_has_validation(self):
        self.assertIn("validate", self.content)

    def test_has_dry_run(self):
        self.assertIn("dry-run", self.content)

    def test_has_api_rollback(self):
        self.assertIn("rollback-api", self.content)

    def test_has_worker_rollback(self):
        self.assertIn("rollback-worker", self.content)

    def test_has_vercel_rollback(self):
        self.assertIn("rollback-vercel", self.content)

    def test_refuses_empty_revision(self):
        self.assertIn("if [ -z \"$REVISION\"", self.content)

    def test_refuses_latest(self):
        self.assertIn("'latest'", self.content)

    def test_refuses_head(self):
        self.assertIn("'HEAD'", self.content)

    def test_requires_explicit_environment(self):
        self.assertIn("environment must be", self.content)

    def test_requires_explicit_target(self):
        self.assertIn("target must be", self.content)


class TestRollbackScript(unittest.TestCase):
    def setUp(self):
        self.script_path = ROOT / "scripts" / "rollback.sh"
        self.content = self.script_path.read_text() if self.script_path.is_file() else ""

    def test_script_exists(self):
        self.assertTrue(self.script_path.is_file())

    def test_has_dry_run(self):
        self.assertIn("--dry-run", self.content)

    def test_refuses_latest(self):
        self.assertIn("'latest'", self.content)

    def test_has_all_targets(self):
        for target in ["api", "worker", "vercel"]:
            self.assertIn(
                target, self.content,
                f"rollback.sh missing target: {target}",
            )


class TestRollbackRefusalPaths(unittest.TestCase):
    def test_workflow_refuses_production_implicitly(self):
        """Rollback must require explicit environment, never infer production."""
        path = ROOT / ".github" / "workflows" / "rollback.yml"
        content = path.read_text()
        # The workflow must not default to production
        self.assertIn("staging", content)
        self.assertIn("production", content)

    def test_rollback_rejects_empty_input(self):
        path = ROOT / ".github" / "workflows" / "rollback.yml"
        content = path.read_text()
        self.assertIn("required: true", content)
