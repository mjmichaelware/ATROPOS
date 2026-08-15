"""The human- and model-readable implementation blueprint.

The only artifact meant to be *read* rather than parsed, which is why it is
separate: every other builder answers to a schema, and this one answers to
whether a person or a model can follow it.

Includes the execution-plan walk-through -- the DAG rendered as an order of work
-- because a flat list of requirements gives a build agent no way to know what
to do first.
"""

from __future__ import annotations

import json
import sqlite3



def build_execution_plan_section(
    plan: dict[str, object],
) -> list[str]:
    # atropos_handoff.json already carries the full DAG (nodes, edges,
    # ready_node_ids) as structured JSON, but the human/LLM-readable
    # blueprint previously only mapped each atom to the graph node it
    # produced - it never rendered the graph itself: what every node
    # actually is, what depends on what, or what can start right now.
    # A build agent working from the PDF/text alone had no execution
    # order to follow, only a flat list of requirements. This section
    # is that missing DAG walk-through.
    execution_graph = plan["execution_graph"]
    nodes = list(execution_graph["nodes"])
    edges = list(execution_graph["edges"])
    ready_nodes = list(plan["ready_nodes"])
    nodes_by_id = {
        str(node["id"]): node
        for node in nodes
    }

    def describe_node(node_id: object) -> str:
        node = nodes_by_id.get(str(node_id))
        if node is None:
            return f"`{node_id}`"
        return (
            f"`{node['node_key']}` "
            f"({node['node_type']}, "
            f"{node['status']}): "
            f"{node['title']}"
        )

    lines = [
        "## Execution Plan (DAG)",
        "",
        (
            "This is the build plan itself, not just the "
            "requirements it was built from: every node Atropos (or "
            "any executing agent) must complete, what each node "
            "depends on, and what is safe to start immediately. "
            "Nodes only ever run CONTRACT before IMPLEMENTATION "
            "before VERIFICATION for a given requirement, and "
            "dependencies below are the enforced (acyclic) order - "
            "nothing may start before its predecessors reach "
            "COMPLETE."
        ),
        "",
        f"- Total nodes: {len(nodes)}",
        f"- Total dependencies: {len(edges)}",
        f"- Ready to start now: {len(ready_nodes)}",
        "",
        "### Ready to start now",
        "",
    ]

    if ready_nodes:
        for node in ready_nodes:
            lines.append(f"- {describe_node(node['id'])}")
    else:
        lines.append(
            "- None - every node is either already complete or "
            "waiting on a predecessor."
        )

    lines.extend(["", "### Nodes", ""])

    for node in nodes:
        lines.append(
            f"- `{node['node_key']}` "
            f"[{node['status']}] "
            f"({node['node_type']}): "
            f"{node['title']}"
        )

    lines.extend(["", "### Dependencies", ""])

    if edges:
        for edge in edges:
            lines.append(
                f"- {describe_node(edge['from_node_id'])} "
                f"MUST COMPLETE before "
                f"{describe_node(edge['to_node_id'])} "
                f"can start "
                f"({edge['edge_type']})."
            )
            rationale = edge.get("rationale")
            if rationale:
                lines.append(f"  - Why: {rationale}")
    else:
        lines.append("- No dependencies recorded.")

    lines.append("")
    return lines


def build_markdown(
    project: dict[str, object],
    plan: dict[str, object],
    atoms: list[
        dict[str, object]
    ],
    traceability: list[
        dict[str, object]
    ],
    bindings: list[
        dict[str, object]
    ],
) -> str:
    lines = [
        (
            f"# Implementation Blueprint: "
            f"{project['name']}"
        ),
        "",
        "## Identity",
        "",
        f"- Project ID: `{project['id']}`",
        f"- Project slug: `{project['slug']}`",
        f"- Plan ID: `{plan['id']}`",
        (
            "- Plan fingerprint: "
            f"`{plan['input_fingerprint']}`"
        ),
        f"- Plan status: `{plan['status']}`",
        "",
        "## Scope",
        "",
        (
            f"- Atomic requirements: "
            f"{len(atoms)}"
        ),
        (
            f"- Execution nodes: "
            f"{plan['node_count']}"
        ),
        (
            f"- Execution edges: "
            f"{plan['edge_count']}"
        ),
        (
            f"- Open research dimensions: "
            f"{plan['open_dimension_count']}"
        ),
        "",
    ]

    lines.extend(
        build_execution_plan_section(
            plan=plan,
        )
    )

    lines.extend(
        [
            "## Atomic Requirements",
            "",
        ]
    )

    for index, item in enumerate(
        traceability,
        start=1,
    ):
        source = item["source"]

        lines.extend(
            [
                (
                    f"### {index}. "
                    f"{item['statement']}"
                ),
                "",
                (
                    f"- Atom: "
                    f"`{item['atom_id']}`"
                ),
                (
                    f"- Kind: "
                    f"`{item['kind']}`"
                ),
                (
                    f"- Modality: "
                    f"`{item['modality']}`"
                ),
                (
                    "- Source document: "
                    f"`{source['document_id']}`"
                ),
                (
                    "- Source bytes: "
                    f"`{source['byte_start']}:"
                    f"{source['byte_end']}`"
                ),
                (
                    "- Source lines: "
                    f"`{source['line_start']}:"
                    f"{source['line_end']}`"
                ),
                (
                    "- Exact quote: "
                    f"{json.dumps(source['exact_quote'])}"
                ),
                "",
                "Execution stages:",
                "",
            ]
        )

        for binding in item[
            "plan_nodes"
        ]:
            lines.append(
                (
                    f"- `{binding['stage']}` "
                    f"→ `{binding['graph_node_id']}`"
                )
            )

        lines.append("")

    lines.extend(
        [
            "## Integration Bindings",
            "",
        ]
    )

    if bindings:
        for binding in bindings:
            lines.append(
                (
                    f"- `{binding['system_name']}` "
                    f"as "
                    f"`{binding['binding_type']}`"
                )
            )
    else:
        lines.append(
            "- No enabled integration bindings."
        )

    lines.extend(
        [
            "",
            "## Verification Contract",
            "",
            (
                "- Every implementation node must "
                "have a predecessor contract node."
            ),
            (
                "- Every implementation node must "
                "have a successor verification node."
            ),
            (
                "- Execution dependencies must "
                "remain acyclic."
            ),
            (
                "- Source quotes and coordinates "
                "must remain traceable."
            ),
            (
                "- Runtime evidence must not "
                "replace source authority."
            ),
            "",
        ]
    )

    return "\n".join(lines)
