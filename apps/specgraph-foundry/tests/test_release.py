from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class TestReleaseSHA(unittest.TestCase):
    def test_deploy_workflows_verify_sha(self):
        """Deploy workflows must verify the exact commit SHA."""
        for wf_name in ("deploy-cloud-run-api.yml", "deploy-cloud-run-worker.yml", "deploy-vercel.yml"):
            path = ROOT / ".github" / "workflows" / wf_name
            self.assertTrue(path.is_file(), f"{wf_name} missing")
            content = path.read_text()
            self.assertIn("Verify release SHA", content)
            self.assertIn("EXPECTED", content)
            self.assertIn("ACTUAL", content)

    def test_rollback_rejects_sha_latest_and_head(self):
        wf_path = ROOT / ".github" / "workflows" / "rollback.yml"
        content = wf_path.read_text()
        self.assertIn("'latest'", content)
        self.assertIn("'HEAD'", content)
        self.assertIn("if [ -z \"$REVISION\"", content)


class TestEnvironmentBoundary(unittest.TestCase):
    def test_production_requires_approval(self):
        """Production environments should reference environment protection."""
        for wf_name in ("deploy-cloud-run-api.yml", "deploy-cloud-run-worker.yml", "deploy-vercel.yml", "rollback.yml"):
            path = ROOT / ".github" / "workflows" / wf_name
            content = path.read_text()
            self.assertIn("environment:", content)

    def test_supabase_audit_staging_only(self):
        path = ROOT / ".github" / "workflows" / "supabase-hosted-audit.yml"
        content = path.read_text()
        self.assertIn("staging", content)
        self.assertNotIn("production", content)


class TestCIComprehensiveness(unittest.TestCase):
    def test_ci_has_backend_and_web(self):
        path = ROOT / ".github" / "workflows" / "ci.yml"
        content = path.read_text()
        self.assertIn("backend:", content)
        self.assertIn("web:", content)
        self.assertIn("deployment-checks:", content)

    def test_ci_has_git_diff_check(self):
        path = ROOT / ".github" / "workflows" / "ci.yml"
        content = path.read_text()
        self.assertIn("git diff --check", content)


class TestRunbookCompletion(unittest.TestCase):
    def test_required_runbooks_exist(self):
        required = [
            "STAGING_DEPLOYMENT",
            "PRODUCTION_DEPLOYMENT",
            "ROLLBACK",
            "FAILED_DEPLOYMENT",
            "INCIDENT_RESPONSE",
            "SECRET_EXPOSURE",
            "MIGRATION_FAILURE",
            "CLOUD_RUN_REVISION_RECOVERY",
            "VERCEL_ROLLBACK",
            "SUPABASE_HOSTED_AUDIT",
            "HEALTH_READINESS_DIAGNOSIS",
            "TELEMETRY_VALIDATION",
            "BACKUP_AND_RESTORE",
            "PRODUCTION_APPROVAL",
            "OWNER_ONLY_PLATFORM_SETUP",
        ]
        runbooks_dir = ROOT / "docs" / "runbooks"
        existing = {f.stem for f in runbooks_dir.glob("*.md")} if runbooks_dir.is_dir() else set()
        missing = [r for r in required if r not in existing]
        self.assertFalse(
            missing,
            f"Missing runbooks: {missing}",
        )
