import tempfile
import unittest
import uuid
from pathlib import Path

from specgraph_foundry.atoms import (
    AtomService,
)
from specgraph_foundry.database import (
    Database,
)
from specgraph_foundry.execution import (
    ExecutionService,
)
from specgraph_foundry.ingestion import (
    IngestionService,
)
from specgraph_foundry.planning import (
    PlanningService,
)
from specgraph_foundry.research import (
    ResearchService,
)
from specgraph_foundry.services import (
    ProjectService,
)


class ExecutionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )

        self.database = Database(
            Path(self.temp.name)
            / "test.sqlite3"
        )
        self.database.initialize()

        self.projects = ProjectService(
            self.database
        )
        self.ingestion = (
            IngestionService(
                self.database
            )
        )
        self.atoms = AtomService(
            self.database
        )
        self.research = (
            ResearchService(
                self.database
            )
        )
        self.planning = (
            PlanningService(
                self.database
            )
        )
        self.execution = (
            ExecutionService(
                self.database
            )
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _project_and_plan(
        self,
        resolved: bool,
    ) -> tuple[
        dict[str, object],
        dict[str, object],
        dict[str, object],
    ]:
        suffix = uuid.uuid4().hex[:8]

        project = self.projects.create(
            f"execution-{suffix}",
            "Execution Test",
        )

        document = (
            self.ingestion.ingest_text(
                project_id=str(
                    project["id"]
                ),
                title="Authority",
                content=(
                    "The service must preserve "
                    "source authority.\n"
                ),
                chunk_bytes=32,
            )
        )

        extraction = (
            self.atoms.extract_document(
                str(document["id"])
            )
        )

        atom = extraction["atoms"][0]

        if resolved:
            count = 0

            while True:
                worker = (
                    f"researcher-{count}"
                )

                task = (
                    self.research.claim_task(
                        str(project["id"]),
                        worker,
                        300,
                    )
                )

                if task is None:
                    break

                evidence = (
                    self.research.add_evidence(
                        task_id=str(
                            task["id"]
                        ),
                        worker_id=worker,
                        source_uri=(
                            "urn:test:"
                            + str(task["id"])
                        ),
                        source_title=(
                            "Test Authority"
                        ),
                        excerpt=(
                            "The requirement is "
                            "applicable."
                        ),
                        evidence_type=(
                            "USER_DECISION"
                        ),
                        reliability=1.0,
                    )
                )

                self.research.complete_task(
                    task_id=str(
                        task["id"]
                    ),
                    worker_id=worker,
                    conclusion=(
                        "This dimension applies "
                        "to the requirement."
                    ),
                    applicability=(
                        "APPLICABLE"
                    ),
                    confidence=1.0,
                    evidence_ids=[
                        str(evidence["id"])
                    ],
                )

                count += 1

        plan = self.planning.synthesize(
            str(project["id"]),
            allow_open_research=(
                not resolved
            ),
        )

        return project, atom, plan

    def _start(
        self,
        plan: dict[str, object],
    ) -> dict[str, object]:
        return self.execution.start_run(
            plan_id=str(plan["id"]),
            runtime_system="ATROPOS",
            runtime_run_id=(
                "runtime-"
                + uuid.uuid4().hex
            ),
        )

    @staticmethod
    def _contract_evidence(
        atom_id: str,
    ) -> dict[str, object]:
        return {
            "source_atom_ids": [
                atom_id
            ],
            "acceptance_criteria": [
                (
                    "The service preserves "
                    "source authority."
                )
            ],
        }

    @staticmethod
    def _implementation_evidence(
        atom_id: str,
    ) -> dict[str, object]:
        return {
            "source_atom_ids": [
                atom_id
            ],
            "changed_files": [
                {
                    "path": (
                        "src/service.py"
                    ),
                    "sha256": "a" * 64,
                    "responsibility": (
                        "Implements source-authority "
                        "preservation."
                    ),
                }
            ],
            "commands": [
                {
                    "command": (
                        "python -m unittest"
                    ),
                    "exit_code": 0,
                    "stdout_sha256": (
                        "b" * 64
                    ),
                }
            ],
            "diff_sha256": "c" * 64,
            "call_sites": [
                "src/app.py:main"
            ],
            "reachability": [
                "POST /v1/service"
            ],
            "rollback": {
                "strategy": (
                    "Revert the implementation "
                    "commit."
                ),
                "recovery_command": (
                    "git revert HEAD"
                ),
            },
        }

    @staticmethod
    def _verification_evidence(
        atom_id: str,
        implementation_receipt_id: str,
    ) -> dict[str, object]:
        return {
            "source_atom_ids": [
                atom_id
            ],
            "commands": [
                {
                    "command": (
                        "python -m unittest"
                    ),
                    "exit_code": 0,
                }
            ],
            "tests": [
                {
                    "name": (
                        "source authority "
                        "is preserved"
                    ),
                    "status": "PASSED",
                    "assertions": 3,
                }
            ],
            "independent_verification": (
                True
            ),
            "verified_receipt_ids": [
                implementation_receipt_id
            ],
        }

    def _complete_contract(
        self,
        run_id: str,
        atom_id: str,
    ) -> dict[str, object]:
        claim = self.execution.claim_node(
            run_id,
            "contract-worker",
            lease_seconds=300,
        )

        self.assertEqual(
            claim["node"]["stage"],
            "CONTRACT",
        )

        return self.execution.submit_receipt(
            run_node_id=str(
                claim["node"]["id"]
            ),
            worker_id="contract-worker",
            actor_system="ATROPOS",
            outcome="SUCCESS",
            summary=(
                "Defined concrete acceptance "
                "criteria for source authority."
            ),
            evidence=self._contract_evidence(
                atom_id
            ),
        )

    def _complete_implementation(
        self,
        run_id: str,
        atom_id: str,
        worker: str = "builder",
    ) -> dict[str, object]:
        claim = self.execution.claim_node(
            run_id,
            worker,
            lease_seconds=300,
        )

        self.assertEqual(
            claim["node"]["stage"],
            "IMPLEMENTATION",
        )

        return self.execution.submit_receipt(
            run_node_id=str(
                claim["node"]["id"]
            ),
            worker_id=worker,
            actor_system="ATROPOS",
            outcome="SUCCESS",
            summary=(
                "Implemented source-authority "
                "preservation with connected "
                "runtime call sites."
            ),
            evidence=(
                self._implementation_evidence(
                    atom_id
                )
            ),
        )

    def _complete_verification(
        self,
        run_id: str,
        atom_id: str,
        implementation_receipt_id: str,
        worker: str = "verifier",
    ) -> dict[str, object]:
        claim = self.execution.claim_node(
            run_id,
            worker,
            lease_seconds=300,
        )

        self.assertEqual(
            claim["node"]["stage"],
            "VERIFICATION",
        )

        return self.execution.submit_receipt(
            run_node_id=str(
                claim["node"]["id"]
            ),
            worker_id=worker,
            actor_system="ATROPOS",
            outcome="SUCCESS",
            summary=(
                "Independently verified source "
                "authority behavior with "
                "assertion-bearing tests."
            ),
            evidence=(
                self._verification_evidence(
                    atom_id,
                    implementation_receipt_id,
                )
            ),
        )

    def test_valid_execution_flow(
        self,
    ) -> None:
        _, atom, plan = (
            self._project_and_plan(
                resolved=True
            )
        )

        run = self._start(plan)
        run_id = str(run["id"])
        atom_id = str(atom["id"])

        self._complete_contract(
            run_id,
            atom_id,
        )

        implementation = (
            self._complete_implementation(
                run_id,
                atom_id,
            )
        )

        self.assertEqual(
            implementation[
                "validation_status"
            ],
            "ACCEPTED",
        )

        verification = (
            self._complete_verification(
                run_id,
                atom_id,
                str(
                    implementation["id"]
                ),
            )
        )

        self.assertEqual(
            verification[
                "validation_status"
            ],
            "ACCEPTED",
        )

        result = (
            self.execution.verify_run(
                run_id
            )
        )

        self.assertTrue(
            result["valid"]
        )
        self.assertEqual(
            result["status"],
            "VERIFIED",
        )

    def test_empty_implementation_rejected(
        self,
    ) -> None:
        _, atom, plan = (
            self._project_and_plan(
                resolved=True
            )
        )

        run = self._start(plan)
        run_id = str(run["id"])
        atom_id = str(atom["id"])

        self._complete_contract(
            run_id,
            atom_id,
        )

        claim = self.execution.claim_node(
            run_id,
            "builder",
            lease_seconds=300,
        )

        receipt = (
            self.execution.submit_receipt(
                run_node_id=str(
                    claim["node"]["id"]
                ),
                worker_id="builder",
                actor_system="ATROPOS",
                outcome="SUCCESS",
                summary=(
                    "Reported implementation "
                    "without concrete changes."
                ),
                evidence={
                    "source_atom_ids": [
                        atom_id
                    ]
                },
            )
        )

        self.assertEqual(
            receipt[
                "validation_status"
            ],
            "REJECTED",
        )

        codes = {
            finding["gate_code"]
            for finding
            in receipt["findings"]
        }

        self.assertIn(
            "NO_EMPTY_IMPLEMENTATION",
            codes,
        )
        self.assertIn(
            (
                "NO_DISCONNECTED_PUBLIC_"
                "COMPONENT"
            ),
            codes,
        )
        self.assertIn(
            "NO_UNREACHABLE_FEATURE",
            codes,
        )

    def test_self_verification_rejected(
        self,
    ) -> None:
        _, atom, plan = (
            self._project_and_plan(
                resolved=True
            )
        )

        run = self._start(plan)
        run_id = str(run["id"])
        atom_id = str(atom["id"])

        self._complete_contract(
            run_id,
            atom_id,
        )

        implementation = (
            self._complete_implementation(
                run_id,
                atom_id,
                worker="builder",
            )
        )

        verification = (
            self._complete_verification(
                run_id,
                atom_id,
                str(
                    implementation["id"]
                ),
                worker="builder",
            )
        )

        self.assertEqual(
            verification[
                "validation_status"
            ],
            "REJECTED",
        )

        codes = {
            finding["gate_code"]
            for finding
            in verification[
                "findings"
            ]
        }

        self.assertIn(
            "NO_SELF_VERIFICATION",
            codes,
        )

    def test_unresearched_implementation_rejected(
        self,
    ) -> None:
        _, atom, plan = (
            self._project_and_plan(
                resolved=False
            )
        )

        run = self._start(plan)
        run_id = str(run["id"])
        atom_id = str(atom["id"])

        self._complete_contract(
            run_id,
            atom_id,
        )

        receipt = (
            self._complete_implementation(
                run_id,
                atom_id,
            )
        )

        self.assertEqual(
            receipt[
                "validation_status"
            ],
            "REJECTED",
        )

        codes = {
            finding["gate_code"]
            for finding
            in receipt["findings"]
        }

        self.assertIn(
            (
                "NO_UNRESEARCHED_"
                "IMPLEMENTATION"
            ),
            codes,
        )

    def test_tampering_invalidates_run(
        self,
    ) -> None:
        _, atom, plan = (
            self._project_and_plan(
                resolved=True
            )
        )

        run = self._start(plan)
        run_id = str(run["id"])
        atom_id = str(atom["id"])

        self._complete_contract(
            run_id,
            atom_id,
        )

        implementation = (
            self._complete_implementation(
                run_id,
                atom_id,
            )
        )

        self._complete_verification(
            run_id,
            atom_id,
            str(implementation["id"]),
        )

        first = self.execution.verify_run(
            run_id
        )

        self.assertTrue(first["valid"])

        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE execution_receipts
                SET evidence_json = ?
                WHERE id = ?
                """,
                (
                    '{"tampered":true}',
                    implementation["id"],
                ),
            )

        second = (
            self.execution.verify_run(
                run_id
            )
        )

        self.assertFalse(
            second["valid"]
        )

        codes = {
            finding["gate_code"]
            for finding
            in second["findings"]
        }

        self.assertIn(
            "EVIDENCE_HASH_MISMATCH",
            codes,
        )


if __name__ == "__main__":
    unittest.main()
