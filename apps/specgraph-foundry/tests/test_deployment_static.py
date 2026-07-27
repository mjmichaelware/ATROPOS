from __future__ import annotations

import os
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class TestDockerfiles(unittest.TestCase):
    def test_api_dockerfile_exists(self):
        self.assertTrue((ROOT / "Dockerfile.api").is_file())

    def test_worker_dockerfile_exists(self):
        self.assertTrue((ROOT / "Dockerfile.worker").is_file())

    def test_api_dockerfile_pins_base(self):
        content = (ROOT / "Dockerfile.api").read_text()
        self.assertIn("python:3.11-slim@sha256:", content)

    def test_api_dockerfile_has_nonroot(self):
        content = (ROOT / "Dockerfile.api").read_text()
        self.assertIn("USER specgraph", content)

    def test_api_dockerfile_has_healthcheck(self):
        content = (ROOT / "Dockerfile.api").read_text()
        self.assertIn("HEALTHCHECK", content)

    def test_api_dockerfile_has_port(self):
        content = (ROOT / "Dockerfile.api").read_text()
        self.assertIn("EXPOSE 8080", content)
        self.assertIn("PORT=8080", content)

    def test_worker_dockerfile_pins_base(self):
        content = (ROOT / "Dockerfile.worker").read_text()
        self.assertIn("python:3.11-slim@sha256:", content)

    def test_worker_dockerfile_has_nonroot(self):
        content = (ROOT / "Dockerfile.worker").read_text()
        self.assertIn("USER specgraph", content)

    def test_worker_dockerfile_has_entrypoint(self):
        content = (ROOT / "Dockerfile.worker").read_text()
        self.assertIn("ENTRYPOINT", content)
        self.assertIn("worker", content)


class TestDockerignore(unittest.TestCase):
    def test_exists(self):
        self.assertTrue((ROOT / ".dockerignore").is_file())

    def test_contains_git(self):
        content = (ROOT / ".dockerignore").read_text()
        self.assertIn(".git/", content)

    def test_contains_pycache(self):
        content = (ROOT / ".dockerignore").read_text()
        self.assertIn("__pycache__/", content)


class TestVercelConfig(unittest.TestCase):
    def test_vercel_json_exists(self):
        found = (ROOT / "vercel.json").is_file() or (ROOT / "apps" / "web" / "vercel.json").is_file()
        self.assertTrue(found, "vercel.json not found in root or apps/web")


class TestWorkflowExistence(unittest.TestCase):
    def test_ci_workflow(self):
        self.assertTrue((ROOT / ".github" / "workflows" / "ci.yml").is_file())

    def test_deploy_api_workflow(self):
        self.assertTrue(
            (ROOT / ".github" / "workflows" / "deploy-cloud-run-api.yml").is_file()
        )

    def test_deploy_worker_workflow(self):
        self.assertTrue(
            (ROOT / ".github" / "workflows" / "deploy-cloud-run-worker.yml").is_file()
        )

    def test_deploy_vercel_workflow(self):
        self.assertTrue(
            (ROOT / ".github" / "workflows" / "deploy-vercel.yml").is_file()
        )

    def test_rollback_workflow(self):
        self.assertTrue((ROOT / ".github" / "workflows" / "rollback.yml").is_file())

    def test_supabase_audit_workflow(self):
        self.assertTrue(
            (ROOT / ".github" / "workflows" / "supabase-hosted-audit.yml").is_file()
        )


class TestRollbackScript(unittest.TestCase):
    def test_script_exists(self):
        path = ROOT / "scripts" / "rollback.sh"
        self.assertTrue(path.is_file())
        self.assertTrue(os.access(str(path), os.X_OK))


class TestDeploymentScripts(unittest.TestCase):
    def test_check_deployment_readiness(self):
        self.assertTrue((ROOT / "scripts" / "check_deployment_readiness.py").is_file())

    def test_check_rollback(self):
        self.assertTrue((ROOT / "scripts" / "check_rollback.py").is_file())

    def test_check_secrets(self):
        self.assertTrue((ROOT / "scripts" / "check_secrets.py").is_file())


class TestRunbooks(unittest.TestCase):
    def test_runbooks_directory(self):
        dirpath = ROOT / "docs" / "runbooks"
        self.assertTrue(dirpath.is_dir())
        files = list(dirpath.glob("*.md"))
        self.assertGreaterEqual(len(files), 10, f"Only {len(files)} runbooks found")


class TestHealthContracts(unittest.TestCase):
    def test_health_endpoint_contract(self):
        health_path = ROOT / "src" / "specgraph_foundry" / "http_api" / "health.py"
        self.assertTrue(health_path.is_file())
        content = health_path.read_text()
        self.assertIn("live_response", content)
        self.assertIn("startup_response", content)
        self.assertIn("readiness_response", content)
