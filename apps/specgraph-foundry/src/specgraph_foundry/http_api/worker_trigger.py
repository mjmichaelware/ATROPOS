from __future__ import annotations

import json
import logging
import threading
import urllib.error
import urllib.request
from dataclasses import dataclass

logger = logging.getLogger(__name__)

_METADATA_TOKEN_URL = (
    "http://metadata.google.internal"
    "/computeMetadata/v1/instance/service-accounts/default/token"
)
_CLOUD_RUN_JOBS_URL = (
    "https://run.googleapis.com/v2"
    "/projects/{project}/locations/{region}/jobs/{job}:run"
)


@dataclass(frozen=True)
class CloudRunWorkerTrigger:
    """Fires the Cloud Run worker job immediately after an operation is queued.

    Best-effort: failures are logged but never surface to the caller.
    The Cloud Scheduler fallback still picks up operations if this fails.
    """

    project_id: str
    region: str
    job_name: str

    def kick(self) -> None:
        threading.Thread(target=self._execute, daemon=True).start()

    def _get_access_token(self) -> str:
        req = urllib.request.Request(
            _METADATA_TOKEN_URL,
            headers={"Metadata-Flavor": "Google"},
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read())
        return str(data["access_token"])

    def _execute(self) -> None:
        try:
            token = self._get_access_token()
            url = _CLOUD_RUN_JOBS_URL.format(
                project=self.project_id,
                region=self.region,
                job=self.job_name,
            )
            req = urllib.request.Request(
                url,
                data=b"{}",
                method="POST",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/json",
                },
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                resp.read()
            logger.info(
                "worker trigger: job execution started",
                extra={"job": self.job_name},
            )
        except urllib.error.HTTPError as exc:
            # 409 means an execution is already running and will drain the
            # queue - that's fine, the operation will still be picked up.
            if exc.code == 409:
                logger.debug("worker trigger: execution already running")
            else:
                logger.warning(
                    "worker trigger: HTTP %s (non-fatal)", exc.code
                )
        except Exception as exc:
            logger.warning("worker trigger: failed (non-fatal): %s", exc)
