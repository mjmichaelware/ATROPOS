"""The ATROPOS handoff document.

`atropos_handoff.json`, schema `specgraph.atropos.handoff.v1`: the execution DAG,
the requirement traceability, the integration bindings, the routing law and the
execution contract naming atropos as runtime owner.

Its own module because it is the one artifact addressed to a specific consumer.
Everything else in an export describes the plan; this one is a contract with the
runtime that will execute it.
"""

from __future__ import annotations

import json
import sqlite3



def build_handoff(
    project: dict[str, object],
    plan: dict[str, object],
    traceability: list[
        dict[str, object]
    ],
    bindings: list[
        dict[str, object]
    ],
) -> dict[str, object]:
    execution_graph = plan[
        "execution_graph"
    ]

    ready_node_ids = [
        node["id"]
        for node in plan[
            "ready_nodes"
        ]
    ]

    requirements = []

    for item in traceability:
        requirements.append(
            {
                "atom_id": item[
                    "atom_id"
                ],
                "statement": item[
                    "statement"
                ],
                "kind": item["kind"],
                "modality": item[
                    "modality"
                ],
                "source": item[
                    "source"
                ],
                "plan_nodes": item[
                    "plan_nodes"
                ],
            }
        )

    return {
        "schema": (
            "specgraph.atropos.handoff.v1"
        ),
        "producer": (
            "specgraph-foundry"
        ),
        "project": {
            "id": project["id"],
            "slug": project["slug"],
            "name": project["name"],
        },
        "plan": {
            "id": plan["id"],
            "status": plan["status"],
            "input_fingerprint": (
                plan[
                    "input_fingerprint"
                ]
            ),
            "authority_graph_id": (
                plan[
                    "authority_graph_id"
                ]
            ),
            "execution_graph_id": (
                plan[
                    "execution_graph_id"
                ]
            ),
        },
        "execution": {
            "graph_id": (
                execution_graph["id"]
            ),
            "nodes": (
                execution_graph["nodes"]
            ),
            "edges": (
                execution_graph["edges"]
            ),
            "ready_node_ids": (
                ready_node_ids
            ),
        },
        "requirements": requirements,
        "integration_bindings": (
            bindings
        ),
        "routing_law": [
            "LOCAL_TOOLCHAIN",
            "FREE_READY_PROVIDER",
            "FREE_FALLBACK_PROVIDER",
            "COOLDOWN_QUEUE",
            "OFFLINE_DEGRADED_MODE",
            (
                "PAID_EMERGENCY_ONLY_"
                "BY_EXPLICIT_UNLOCK"
            ),
        ],
        "execution_contract": {
            "authority_owner": (
                "specgraph-foundry"
            ),
            "runtime_owner": (
                "atropos"
            ),
            "source_authority_is_immutable": (
                True
            ),
            "execution_graph_must_be_acyclic": (
                True
            ),
            "implementation_requires_"
            "verification": True,
        },
    }
