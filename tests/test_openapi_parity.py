import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OPENAPI_PATH = (
    ROOT / "openapi" / "specgraph-v1.yaml"
)

HTTP_METHODS = {
    "get",
    "post",
    "put",
    "patch",
    "delete",
    "options",
    "head",
    "trace",
}

EXPECTED_OPERATIONS = {
    ("GET", "/health"),
    ("GET", "/health/live"),
    ("GET", "/health/startup"),
    ("GET", "/health/ready"),
    ("GET", "/version"),
    ("GET", "/v1/me"),
    ("GET", "/v1/projects"),
    ("POST", "/v1/projects"),
    ("GET", "/v1/projects/{project_id}"),
    ("GET", "/v1/projects/{project_id}/workspace"),
    ("GET", "/v1/projects/{project_id}/readiness"),
    ("GET", "/v1/projects/{project_id}/source-workspace"),
    ("GET", "/v1/projects/{project_id}/research-workspace"),
    ("GET", "/v1/projects/{project_id}/planning-workspace"),
    ("GET", "/v1/projects/{project_id}/handoff-workspace"),
    ("GET", "/v1/projects/{project_id}/documents"),
    ("POST", "/v1/projects/{project_id}/documents"),
    ("POST", "/v1/projects/{project_id}/source-uploads"),
    ("GET", "/v1/documents/{document_id}"),
    ("GET", "/v1/documents/{document_id}/verify"),
    ("POST", "/v1/documents/{document_id}/extract"),
    ("GET", "/v1/documents/{document_id}/atoms"),
    ("GET", "/v1/documents/{document_id}/provenance"),
    ("GET", "/v1/source-uploads/{upload_id}"),
    ("POST", "/v1/source-uploads/{upload_id}/finalize"),
    ("GET", "/v1/operations/{operation_id}"),
    ("POST", "/v1/operations/{operation_id}/cancel"),
    ("GET", "/v1/projects/{project_id}/operations"),
    ("GET", "/v1/atoms/{atom_id}"),
    ("GET", "/v1/projects/{project_id}/research-tasks"),
    ("POST", "/v1/projects/{project_id}/research-tasks/claim"),
    ("GET", "/v1/projects/{project_id}/gap-matrix"),
    ("GET", "/v1/research-tasks/{task_id}"),
    ("POST", "/v1/research-tasks/{task_id}/heartbeat"),
    ("POST", "/v1/research-tasks/{task_id}/evidence"),
    ("POST", "/v1/research-tasks/{task_id}/complete"),
    ("POST", "/v1/research-tasks/{task_id}/fail"),
    ("GET", "/v1/projects/{project_id}/relations"),
    ("POST", "/v1/projects/{project_id}/relations"),
    ("GET", "/v1/projects/{project_id}/plans"),
    ("POST", "/v1/projects/{project_id}/plans"),
    ("GET", "/v1/plans/{plan_id}"),
    ("POST", "/v1/plans/{plan_id}/verify"),
    ("GET", "/v1/projects/{project_id}/bindings"),
    ("POST", "/v1/projects/{project_id}/bindings"),
    ("GET", "/v1/projects/{project_id}/exports"),
    ("POST", "/v1/plans/{plan_id}/exports"),
    ("GET", "/v1/exports/{export_id}"),
    ("POST", "/v1/exports/{export_id}/verify"),
    ("GET", "/v1/exports/{export_id}/download"),
    ("POST", "/v1/plans/{plan_id}/execution-runs"),
    ("GET", "/v1/projects/{project_id}/execution-runs"),
    ("GET", "/v1/execution-runs/{run_id}"),
    ("POST", "/v1/execution-runs/{run_id}/claim"),
    ("POST", "/v1/execution-runs/{run_id}/verify"),
    ("POST", "/v1/execution-nodes/{run_node_id}/heartbeat"),
    ("POST", "/v1/execution-nodes/{run_node_id}/receipts"),
    ("GET", "/v1/projects/{project_id}/routing-policy"),
    ("POST", "/v1/projects/{project_id}/routing-policy"),
    ("GET", "/v1/projects/{project_id}/providers"),
    ("POST", "/v1/projects/{project_id}/providers"),
    ("POST", "/v1/providers/{provider_id}/health"),
    ("GET", "/v1/projects/{project_id}/renderers"),
    ("POST", "/v1/projects/{project_id}/renderers"),
    ("POST", "/v1/projects/{project_id}/renderers/select"),
    ("POST", "/v1/projects/{project_id}/paid-unlocks"),
    ("POST", "/v1/projects/{project_id}/route-decisions"),
    ("GET", "/v1/route-decisions/{decision_id}"),
}

PAGINATED_COLLECTIONS = {
    "/v1/projects",
    "/v1/projects/{project_id}/documents",
    "/v1/documents/{document_id}/atoms",
    "/v1/projects/{project_id}/research-tasks",
    "/v1/projects/{project_id}/relations",
}

IDEMPOTENT_OPERATIONS = {
    "ingestProjectDocument",
    "createSourceUploadIntent",
    "extractDocumentAtoms",
    "claimProjectResearchTask",
    "addResearchEvidence",
    "completeResearchTask",
    "synthesizeProjectPlan",
    "verifyPlan",
    "createProjectBinding",
    "exportPlan",
    "verifyExport",
    "startExecutionRun",
    "claimExecutionRunNode",
    "submitExecutionReceipt",
    "verifyExecutionRun",
    "createProjectProvider",
    "recordProviderHealth",
    "createProjectRenderer",
    "selectProjectRenderer",
    "grantProjectPaidUnlock",
    "createProjectRouteDecision",
    "finalizeSourceUpload",
    "cancelOperation",
}

CONCURRENCY_OPERATIONS = {
    "setRoutingPolicy",
    "createProjectBinding",
    "createProjectProvider",
    "createProjectRenderer",
}


class OpenApiParityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        with OPENAPI_PATH.open(
            "r",
            encoding="utf-8",
        ) as handle:
            cls.document = json.load(handle)

    def test_openapi_version(self) -> None:
        self.assertEqual(
            self.document["openapi"],
            "3.1.0",
        )

    def test_operation_ids_are_unique(self) -> None:
        operation_ids: list[str] = []

        for _, operation in self._iter_operations():
            operation_id = operation.get(
                "operationId"
            )
            self.assertIsInstance(
                operation_id,
                str,
            )
            self.assertTrue(operation_id)
            operation_ids.append(operation_id)

        self.assertEqual(
            len(operation_ids),
            len(set(operation_ids)),
        )
        self.assertEqual(
            len(operation_ids),
            68,
        )

    def test_inventory_matches_implementation(
        self,
    ) -> None:
        documented = {
            item
            for item, _ in self._iter_operations()
        }

        self.assertEqual(
            documented,
            EXPECTED_OPERATIONS,
        )

    def test_public_routes_are_explicitly_unsecured(
        self,
    ) -> None:
        for path in (
            "/health",
            "/health/live",
            "/health/startup",
            "/health/ready",
            "/version",
        ):
            operation = self.document["paths"][
                path
            ]["get"]
            self.assertEqual(
                operation.get("security"),
                [],
            )

    def test_all_v1_routes_require_bearer_auth(
        self,
    ) -> None:
        for (method, path), operation in (
            self._iter_operations()
        ):
            if not path.startswith("/v1/"):
                continue

            self.assertEqual(
                operation.get("security"),
                [{"bearerAuth": []}],
            )

    def test_all_templated_path_parameters_are_declared(
        self,
    ) -> None:
        for (_, path), operation in (
            self._iter_operations()
        ):
            expected = set(
                re.findall(
                    r"{([^}]+)}",
                    path,
                )
            )

            declared = {
                parameter["name"]
                for parameter in self._resolve_parameters(
                    path,
                    operation,
                )
                if parameter.get("in") == "path"
            }

            self.assertEqual(
                declared,
                expected,
            )

    def test_all_templated_parameters_are_required(
        self,
    ) -> None:
        for (_, path), operation in (
            self._iter_operations()
        ):
            expected = set(
                re.findall(
                    r"{([^}]+)}",
                    path,
                )
            )

            for parameter in self._resolve_parameters(
                path,
                operation,
            ):
                if parameter["name"] not in expected:
                    continue

                self.assertTrue(
                    parameter.get("required"),
                )
                self.assertEqual(
                    parameter.get("in"),
                    "path",
                )

    def test_paginated_collections_declare_limit_and_cursor(
        self,
    ) -> None:
        for path in PAGINATED_COLLECTIONS:
            operation = self.document["paths"][path][
                "get"
            ]
            parameters = {
                parameter["name"]: parameter
                for parameter in self._resolve_parameters(
                    path,
                    operation,
                )
            }

            self.assertIn("limit", parameters)
            self.assertIn("cursor", parameters)
            self.assertEqual(
                parameters["limit"]["in"],
                "query",
            )
            self.assertEqual(
                parameters["cursor"]["in"],
                "query",
            )

    def test_paginated_collections_declare_page_headers(
        self,
    ) -> None:
        for path in PAGINATED_COLLECTIONS:
            headers = self.document["paths"][path][
                "get"
            ]["responses"]["200"]["headers"]
            self.assertIn(
                "x-page-limit",
                headers,
            )
            self.assertIn(
                "x-page-count",
                headers,
            )
            self.assertIn(
                "x-has-more",
                headers,
            )
            self.assertIn(
                "x-next-cursor",
                headers,
            )

    def test_idempotent_mutations_require_idempotency_key(
        self,
    ) -> None:
        for _, operation in self._iter_operations():
            operation_id = operation["operationId"]

            if operation_id not in IDEMPOTENT_OPERATIONS:
                continue

            names = {
                parameter["name"]
                for parameter in self._resolve_parameters(
                    "",
                    operation,
                )
                if parameter.get("in") == "header"
            }
            self.assertIn(
                "Idempotency-Key",
                names,
            )

    def test_download_export_does_not_require_idempotency_key(
        self,
    ) -> None:
        operation = self.document["paths"][
            "/v1/exports/{export_id}/download"
        ]["get"]
        self.assertEqual(
            operation["operationId"],
            "downloadExportArtifacts",
        )
        names = {
            parameter["name"]
            for parameter in self._resolve_parameters(
                "",
                operation,
            )
            if parameter.get("in") == "header"
        }
        self.assertNotIn(
            "Idempotency-Key",
            names,
        )
        self.assertIn(
            "409",
            operation["responses"],
        )

    def test_async_mutations_return_operation_contract(
        self,
    ) -> None:
        async_operation_ids = {
            "finalizeSourceUpload",
            "extractDocumentAtoms",
            "completeResearchTask",
            "synthesizeProjectPlan",
            "verifyPlan",
            "exportPlan",
            "verifyExport",
            "startExecutionRun",
            "verifyExecutionRun",
        }
        for _, operation in self._iter_operations():
            if operation["operationId"] not in async_operation_ids:
                continue
            self.assertIn("202", operation["responses"])
            success = operation["responses"]["202"]
            headers = success["headers"]
            self.assertIn("Location", headers)
            self.assertIn("Retry-After", headers)
            schema = success["content"]["application/json"]["schema"]
            self.assertEqual(
                schema["$ref"],
                "#/components/schemas/OperationResponse",
            )

    def test_operation_routes_are_bounded_and_cancel_is_idempotent(
        self,
    ) -> None:
        self.assertIn(
            "Operation",
            self.document["components"]["schemas"],
        )
        cancel = self.document["paths"][
            "/v1/operations/{operation_id}/cancel"
        ]["post"]
        names = {
            parameter["name"]
            for parameter in self._resolve_parameters(
                "",
                cancel,
            )
        }
        self.assertIn("Idempotency-Key", names)
        listing = self.document["paths"][
            "/v1/projects/{project_id}/operations"
        ]["get"]
        query_names = {
            parameter["name"]
            for parameter in self._resolve_parameters(
                "",
                listing,
            )
            if parameter.get("in") == "query"
        }
        self.assertEqual(
            query_names,
            {"limit", "cursor"},
        )

    def test_concurrency_controlled_mutations_require_if_match(
        self,
    ) -> None:
        for _, operation in self._iter_operations():
            operation_id = operation["operationId"]

            if operation_id not in CONCURRENCY_OPERATIONS:
                continue

            names = {
                parameter["name"]
                for parameter in self._resolve_parameters(
                    "",
                    operation,
                )
                if parameter.get("in") == "header"
            }
            self.assertIn(
                "If-Match",
                names,
            )

    def test_idempotent_success_responses_declare_replay_header(
        self,
    ) -> None:
        for _, operation in self._iter_operations():
            operation_id = operation["operationId"]

            if operation_id not in IDEMPOTENT_OPERATIONS:
                continue

            success = (
                operation["responses"].get("201")
                or operation["responses"].get("200")
                or operation["responses"].get("202")
            )
            self.assertIsNotNone(success)
            resolved = self._resolve_ref(success)
            self.assertIn(
                "Idempotency-Replayed",
                resolved["headers"],
            )

    def test_concurrency_responses_declare_precondition_errors(
        self,
    ) -> None:
        for _, operation in self._iter_operations():
            operation_id = operation["operationId"]

            if operation_id not in CONCURRENCY_OPERATIONS:
                continue

            self.assertIn(
                "412",
                operation["responses"],
            )
            self.assertIn(
                "428",
                operation["responses"],
            )

    def test_etag_headers_are_declared_for_editable_resources(
        self,
    ) -> None:
        expected = {
            "getRoutingPolicy",
            "setRoutingPolicy",
            "createProjectBinding",
            "createProjectProvider",
            "createProjectRenderer",
        }

        for _, operation in self._iter_operations():
            operation_id = operation["operationId"]

            if operation_id not in expected:
                continue

            success = (
                operation["responses"].get("201")
                or operation["responses"].get("200")
            )
            self.assertIsNotNone(success)
            resolved = self._resolve_ref(success)
            self.assertIn(
                "ETag",
                resolved["headers"],
            )

    def test_error_responses_use_stable_envelope(
        self,
    ) -> None:
        for _, operation in self._iter_operations():
            if operation["operationId"] in {
                "getHealthStartup",
                "getHealthReady",
            }:
                continue

            for status_code, response in (
                operation.get(
                    "responses",
                    {},
                ).items()
            ):
                if not status_code.isdigit():
                    continue

                if int(status_code) < 400:
                    continue

                response_object = (
                    self._resolve_ref(response)
                )
                schema = (
                    response_object["content"][
                        "application/json"
                    ]["schema"]
                )
                resolved_schema = (
                    self._resolve_ref(schema)
                )

                self.assertEqual(
                    resolved_schema,
                    self.document["components"][
                        "schemas"
                    ]["ErrorEnvelope"],
                )

    def test_source_upload_contract_covers_group06_formats(
        self,
    ) -> None:
        schema = self.document["components"][
            "schemas"
        ]["SourceUploadCreateRequest"]
        media_type = schema["properties"][
            "media_type"
        ]
        self.assertIn(
            "application/pdf",
            media_type["enum"],
        )
        self.assertIn(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            media_type["enum"],
        )
        self.assertIn(
            "text/html",
            media_type["enum"],
        )

    def test_finalize_response_declares_raw_and_derived_authority(
        self,
    ) -> None:
        schema = self.document["components"][
            "schemas"
        ]["SourceUploadFinalizeResponse"]
        self.assertIn(
            "raw_authority",
            schema["required"],
        )
        self.assertIn(
            "derivation",
            schema["required"],
        )
        self.assertEqual(
            schema["properties"]["raw_authority"]["$ref"],
            "#/components/schemas/RawAuthoritySummary",
        )
        self.assertEqual(
            schema["properties"]["derivation"]["$ref"],
            "#/components/schemas/DerivationSummary",
        )

    def test_provenance_schema_distinguishes_raw_and_derived_fields(
        self,
    ) -> None:
        schema = self.document["components"][
            "schemas"
        ]["DocumentProvenanceResponse"]
        extensions = [
            item
            for item in schema["allOf"]
            if isinstance(item, dict)
            and "properties" in item
        ]
        self.assertEqual(len(extensions), 1)
        provenance = extensions[0]["properties"][
            "provenance"
        ]["properties"]
        self.assertIn("raw_authority", provenance)
        self.assertIn("derivation", provenance)

    def test_group06_error_codes_are_documented(
        self,
    ) -> None:
        codes = set(
            self.document["components"]["schemas"][
                "ErrorCode"
            ]["enum"]
        )
        self.assertTrue(
            {
                "INVALID_DOCUMENT",
                "DOCUMENT_ENCRYPTED",
                "DOCUMENT_LIMIT_EXCEEDED",
                "NO_EXTRACTABLE_TEXT",
            }.issubset(codes)
        )

    def test_group09_health_and_resource_contracts_are_documented(
        self,
    ) -> None:
        for path in (
            "/health/live",
            "/health/startup",
            "/health/ready",
        ):
            operation = self.document["paths"][path]["get"]
            self.assertEqual(
                operation.get("security"),
                [],
            )
            self.assertIn(
                "200",
                operation["responses"],
            )

        responses = self.document["components"]["responses"]
        for name in (
            "RequestTargetTooLarge",
            "HeadersTooLarge",
            "TooManyRequests",
            "RequestTimeout",
        ):
            self.assertIn(name, responses)
            schema = responses[name]["content"]["application/json"]["schema"]
            self.assertEqual(
                schema["$ref"],
                "#/components/schemas/ErrorEnvelope",
            )

        codes = set(
            self.document["components"]["schemas"]["ErrorCode"]["enum"]
        )
        self.assertTrue(
            {
                "REQUEST_TARGET_TOO_LARGE",
                "HEADERS_TOO_LARGE",
                "JSON_LIMIT_EXCEEDED",
                "TOO_MANY_REQUESTS",
                "SERVER_BUSY",
                "REQUEST_TIMEOUT",
            }.issubset(codes)
        )

    def _iter_operations(
        self,
    ) -> list[
        tuple[
            tuple[str, str],
            dict[str, object],
        ]
    ]:
        operations: list[
            tuple[
                tuple[str, str],
                dict[str, object],
            ]
        ] = []

        for path, path_item in (
            self.document["paths"].items()
        ):
            for key, operation in path_item.items():
                if key not in HTTP_METHODS:
                    continue

                operations.append(
                    (
                        (key.upper(), path),
                        operation,
                    )
                )

        return operations

    def _resolve_parameters(
        self,
        path: str,
        operation: dict[str, object],
    ) -> list[dict[str, object]]:
        path_item = (
            self.document["paths"][path]
            if path
            else {}
        )
        merged = []

        for parameter in path_item.get(
            "parameters",
            [],
        ) + operation.get("parameters", []):
            merged.append(
                self._resolve_ref(parameter)
            )

        return merged

    def _resolve_ref(
        self,
        value: dict[str, object],
    ) -> dict[str, object]:
        if "$ref" not in value:
            return value

        ref = value["$ref"]
        self.assertIsInstance(ref, str)
        self.assertTrue(
            ref.startswith("#/"),
        )

        current: object = self.document

        for part in ref[2:].split("/"):
            current = current[part]

        self.assertIsInstance(
            current,
            dict,
        )
        return current


if __name__ == "__main__":
    unittest.main()
