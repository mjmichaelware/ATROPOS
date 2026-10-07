from __future__ import annotations

import ast
import sys
from pathlib import Path
from typing import TypeAlias


ROOT = Path(__file__).resolve().parents[1]
AUDIT_PATH = (
    ROOT
    / "scripts"
    / "hosted_release_audit.py"
)
BUILDER_PATH = (
    ROOT
    / "scripts"
    / "build_hosted_release_audit.py"
)
SOURCE_ROOT = (
    ROOT
    / "src"
    / "specgraph_foundry"
)

Schema: TypeAlias = dict[str, "Schema | None"]

BOOLEAN_PARAMETER_NAMES = {
    "allow_open_research",
    "allow_offline_degraded",
    "paid_emergency_enabled",
}


def literal_schema(
    node: ast.AST | None,
) -> Schema | None:
    if not isinstance(node, ast.Dict):
        return None

    result: Schema = {}

    for key, value in zip(
        node.keys,
        node.values,
        strict=True,
    ):
        if not (
            isinstance(key, ast.Constant)
            and isinstance(key.value, str)
        ):
            continue

        result[key.value] = literal_schema(
            value
        )

    return result


def merge_schemas(
    schemas: list[Schema],
) -> Schema:
    if not schemas:
        return {}

    common = set(schemas[0])

    for schema in schemas[1:]:
        common.intersection_update(schema)

    result: Schema = {}

    for key in sorted(common):
        children = [
            schema[key]
            for schema in schemas
        ]

        if all(
            isinstance(child, dict)
            for child in children
        ):
            result[key] = merge_schemas(
                [
                    child
                    for child in children
                    if isinstance(child, dict)
                ]
            )
        else:
            result[key] = None

    return result


def function_schema(
    function: (
        ast.FunctionDef
        | ast.AsyncFunctionDef
    ),
) -> Schema | None:
    returns = [
        node
        for node in ast.walk(function)
        if isinstance(node, ast.Return)
    ]

    if not returns:
        return None

    schemas = [
        literal_schema(node.value)
        for node in returns
    ]

    if any(schema is None for schema in schemas):
        return None

    return merge_schemas(
        [
            schema
            for schema in schemas
            if schema is not None
        ]
    )


def collect_contracts() -> tuple[
    dict[tuple[str, str], Schema],
    dict[str, Schema],
]:
    methods: dict[
        tuple[str, str],
        Schema,
    ] = {}

    functions: dict[str, Schema] = {}

    files = sorted(
        SOURCE_ROOT.glob("*.py")
    ) + [AUDIT_PATH]

    for path in files:
        tree = ast.parse(
            path.read_text(encoding="utf-8"),
            filename=str(path),
        )

        for node in tree.body:
            if isinstance(
                node,
                (
                    ast.FunctionDef,
                    ast.AsyncFunctionDef,
                ),
            ):
                schema = function_schema(node)

                if schema is not None:
                    functions[node.name] = schema

            if isinstance(node, ast.ClassDef):
                for member in node.body:
                    if not isinstance(
                        member,
                        (
                            ast.FunctionDef,
                            ast.AsyncFunctionDef,
                        ),
                    ):
                        continue

                    schema = function_schema(
                        member
                    )

                    if schema is not None:
                        methods[
                            (
                                node.name,
                                member.name,
                            )
                        ] = schema

    return methods, functions


def subscript_chain(
    node: ast.Subscript,
) -> tuple[str, list[str]] | None:
    keys: list[str] = []
    current: ast.AST = node

    while isinstance(
        current,
        ast.Subscript,
    ):
        key = current.slice

        if not (
            isinstance(key, ast.Constant)
            and isinstance(key.value, str)
        ):
            return None

        keys.append(key.value)
        current = current.value

    if not isinstance(current, ast.Name):
        return None

    keys.reverse()
    return current.id, keys


def latest_before(
    entries: list[
        tuple[int, object]
    ],
    line: int,
) -> object | None:
    candidates = [
        value
        for entry_line, value in entries
        if entry_line <= line
    ]

    return (
        candidates[-1]
        if candidates
        else None
    )


def check_audit_contracts() -> list[str]:
    method_contracts, function_contracts = (
        collect_contracts()
    )

    source = AUDIT_PATH.read_text(
        encoding="utf-8"
    )

    tree = ast.parse(
        source,
        filename=str(AUDIT_PATH),
    )

    object_types: dict[
        str,
        list[tuple[int, str]],
    ] = {}

    result_schemas: dict[
        str,
        list[
            tuple[
                int,
                tuple[Schema, str],
            ]
        ],
    ] = {}

    assignments = sorted(
        (
            node
            for node in ast.walk(tree)
            if isinstance(
                node,
                (
                    ast.Assign,
                    ast.AnnAssign,
                ),
            )
        ),
        key=lambda node: node.lineno,
    )

    for node in assignments:
        if isinstance(node, ast.Assign):
            if len(node.targets) != 1:
                continue

            target = node.targets[0]
            value = node.value
        else:
            target = node.target
            value = node.value

        if not (
            isinstance(target, ast.Name)
            and value is not None
        ):
            continue

        variable = target.id

        if isinstance(value, ast.Dict):
            schema = literal_schema(value)

            if schema is not None:
                result_schemas.setdefault(
                    variable,
                    [],
                ).append(
                    (
                        node.lineno,
                        (
                            schema,
                            "dictionary literal",
                        ),
                    )
                )

            continue

        if not isinstance(value, ast.Call):
            continue

        if isinstance(value.func, ast.Name):
            callable_name = value.func.id

            if callable_name.endswith(
                "Service"
            ):
                object_types.setdefault(
                    variable,
                    [],
                ).append(
                    (
                        node.lineno,
                        callable_name,
                    )
                )
                continue

            schema = function_contracts.get(
                callable_name
            )

            if schema is not None:
                result_schemas.setdefault(
                    variable,
                    [],
                ).append(
                    (
                        node.lineno,
                        (
                            schema,
                            callable_name,
                        ),
                    )
                )

            continue

        if not (
            isinstance(
                value.func,
                ast.Attribute,
            )
            and isinstance(
                value.func.value,
                ast.Name,
            )
        ):
            continue

        object_name = value.func.value.id
        method_name = value.func.attr

        class_name = latest_before(
            object_types.get(
                object_name,
                [],
            ),
            node.lineno,
        )

        if not isinstance(class_name, str):
            continue

        schema = method_contracts.get(
            (
                class_name,
                method_name,
            )
        )

        if schema is None:
            continue

        result_schemas.setdefault(
            variable,
            [],
        ).append(
            (
                node.lineno,
                (
                    schema,
                    (
                        f"{class_name}."
                        f"{method_name}"
                    ),
                ),
            )
        )

    for node in ast.walk(tree):
        for child in ast.iter_child_nodes(node):
            setattr(child, "_parent", node)

    problems: list[str] = []

    for node in ast.walk(tree):
        if not isinstance(
            node,
            ast.Subscript,
        ):
            continue

        parent = getattr(
            node,
            "_parent",
            None,
        )

        if isinstance(parent, ast.Subscript):
            continue

        chain = subscript_chain(node)

        if chain is None:
            continue

        variable, keys = chain

        resolved = latest_before(
            result_schemas.get(
                variable,
                [],
            ),
            node.lineno,
        )

        if not (
            isinstance(resolved, tuple)
            and len(resolved) == 2
        ):
            continue

        schema, producer = resolved

        if not isinstance(schema, dict):
            continue

        current: Schema | None = schema

        for key in keys:
            if current is None:
                break

            if key not in current:
                problems.append(
                    (
                        f"{AUDIT_PATH}:"
                        f"{node.lineno}: "
                        f"{variable}[{key!r}] "
                        f"is not returned by "
                        f"{producer}; available keys: "
                        f"{sorted(current)}"
                    )
                )
                break

            current = current[key]

    return problems


def check_boolean_casts() -> list[str]:
    problems: list[str] = []

    for path in sorted(
        SOURCE_ROOT.glob("*.py")
    ):
        tree = ast.parse(
            path.read_text(encoding="utf-8"),
            filename=str(path),
        )

        for node in ast.walk(tree):
            if not (
                isinstance(node, ast.Call)
                and isinstance(
                    node.func,
                    ast.Name,
                )
                and node.func.id == "int"
                and len(node.args) == 1
                and isinstance(
                    node.args[0],
                    ast.Name,
                )
                and node.args[0].id
                in BOOLEAN_PARAMETER_NAMES
            ):
                continue

            problems.append(
                (
                    f"{path}:{node.lineno}: "
                    f"PostgreSQL boolean parameter "
                    f"{node.args[0].id!r} is wrapped "
                    f"in int()"
                )
            )

    return problems


def check_builder_copy() -> list[str]:
    problems: list[str] = []

    builder = BUILDER_PATH.read_text(
        encoding="utf-8"
    )

    if (
        'plan_verification["valid"]'
        in builder
    ):
        problems.append(
            (
                f"{BUILDER_PATH}: obsolete "
                "plan_verification['valid'] "
                "remains in builder"
            )
        )

    runtime = AUDIT_PATH.read_text(
        encoding="utf-8"
    )

    if (
        'plan_verification["valid"]'
        in runtime
    ):
        problems.append(
            (
                f"{AUDIT_PATH}: obsolete "
                "plan_verification['valid'] "
                "remains in runtime audit"
            )
        )

    return problems


def main() -> int:
    problems = (
        check_audit_contracts()
        + check_boolean_casts()
        + check_builder_copy()
    )

    if problems:
        print(
            "HOSTED AUDIT PREFLIGHT FAILED",
            file=sys.stderr,
        )

        for problem in problems:
            print(
                f"- {problem}",
                file=sys.stderr,
            )

        return 1

    print(
        "HOSTED AUDIT CONTRACTS VALID"
    )
    print(
        "POSTGRESQL BOOLEAN PARAMETERS VALID"
    )
    print(
        "BUILDER AND RUNTIME AUDIT MATCH"
    )
    print(
        "HOSTED AUDIT PREFLIGHT PASSED"
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
