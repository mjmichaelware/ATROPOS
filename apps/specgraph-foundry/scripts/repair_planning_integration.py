from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong repository: {ROOT}")


def update(path: str, content: str) -> None:
    target = ROOT / path
    target.write_text(content, encoding="utf-8")
    print(f"UPDATED {path}")


api_path = ROOT / "src/specgraph_foundry/api.py"
api = api_path.read_text(encoding="utf-8")

if "from .planning import PlanningService" not in api:
    marker = "from .research import ResearchService\n"

    if marker not in api:
        raise SystemExit("API research import marker not found")

    api = api.replace(
        marker,
        marker + "from .planning import PlanningService\n",
        1,
    )

if "self.planning = PlanningService(database)" not in api:
    marker = "        self.research = ResearchService(database)\n"

    if marker not in api:
        raise SystemExit("API research service marker not found")

    api = api.replace(
        marker,
        marker + "        self.planning = PlanningService(database)\n",
        1,
    )

if 'parts[3] == "relations"' not in api:
    marker = """            return 404, {
                "error": "ROUTE_NOT_FOUND",
"""

    if marker not in api:
        raise SystemExit("API 404 route marker not found")

    routes = r'''
            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "relations"
            ):
                if method == "GET":
                    return 200, {
                        "items": self.planning.list_relations(
                            parts[2]
                        )
                    }

                if method == "POST":
                    return 201, self.planning.add_relation(
                        project_id=parts[2],
                        from_atom_id=str(
                            payload.get(
                                "from_atom_id",
                                "",
                            )
                        ),
                        to_atom_id=str(
                            payload.get(
                                "to_atom_id",
                                "",
                            )
                        ),
                        relation_type=str(
                            payload.get(
                                "relation_type",
                                "",
                            )
                        ),
                        rationale=str(
                            payload.get(
                                "rationale",
                                "",
                            )
                        ),
                        confidence=float(
                            payload.get(
                                "confidence",
                                1.0,
                            )
                        ),
                        inferred=bool(
                            payload.get(
                                "inferred",
                                False,
                            )
                        ),
                    )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "plans"
            ):
                if method == "GET":
                    return 200, {
                        "items": self.planning.list_plans(
                            parts[2]
                        )
                    }

                if method == "POST":
                    return 201, self.planning.synthesize(
                        project_id=parts[2],
                        allow_open_research=bool(
                            payload.get(
                                "allow_open_research",
                                False,
                            )
                        ),
                    )

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "plans"]
                and method == "GET"
            ):
                return 200, self.planning.get_plan(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "plans"]
                and parts[3] == "verify"
                and method == "POST"
            ):
                return 200, self.planning.verify_plan(
                    parts[2]
                )

'''

    api = api.replace(
        marker,
        routes + marker,
        1,
    )

update(
    "src/specgraph_foundry/api.py",
    api,
)


cli_path = ROOT / "src/specgraph_foundry/cli.py"
cli = cli_path.read_text(encoding="utf-8")

if "from .planning import PlanningService" not in cli:
    marker = "from .research import ResearchService\n"

    if marker not in cli:
        raise SystemExit("CLI research import marker not found")

    cli = cli.replace(
        marker,
        marker + "from .planning import PlanningService\n",
        1,
    )

if '"synthesize-plan"' not in cli:
    marker = '    server = commands.add_parser("serve")\n'

    if marker not in cli:
        raise SystemExit("CLI server parser marker not found")

    parsers = r'''
    relation = commands.add_parser(
        "add-relation"
    )
    relation.add_argument("project_id")
    relation.add_argument("from_atom_id")
    relation.add_argument("to_atom_id")
    relation.add_argument("relation_type")
    relation.add_argument(
        "--rationale",
        default="",
    )
    relation.add_argument(
        "--confidence",
        type=float,
        default=1.0,
    )
    relation.add_argument(
        "--inferred",
        action="store_true",
    )

    relations = commands.add_parser(
        "list-relations"
    )
    relations.add_argument("project_id")

    synthesize = commands.add_parser(
        "synthesize-plan"
    )
    synthesize.add_argument("project_id")
    synthesize.add_argument(
        "--allow-open-research",
        action="store_true",
    )

    plans = commands.add_parser(
        "list-plans"
    )
    plans.add_argument("project_id")

    plan = commands.add_parser("plan")
    plan.add_argument("plan_id")

    verify_plan = commands.add_parser(
        "verify-plan"
    )
    verify_plan.add_argument("plan_id")

'''

    cli = cli.replace(
        marker,
        parsers + marker,
        1,
    )

if "planning = PlanningService(database)" not in cli:
    marker = "    research = ResearchService(database)\n"

    if marker not in cli:
        raise SystemExit("CLI research service marker not found")

    cli = cli.replace(
        marker,
        marker + "    planning = PlanningService(database)\n",
        1,
    )

if 'args.command == "synthesize-plan"' not in cli:
    marker = "    suffix = uuid.uuid4().hex[:8]\n"

    if marker not in cli:
        raise SystemExit("CLI demo marker not found")

    commands = r'''
    if args.command == "add-relation":
        output(
            planning.add_relation(
                project_id=args.project_id,
                from_atom_id=args.from_atom_id,
                to_atom_id=args.to_atom_id,
                relation_type=args.relation_type,
                rationale=args.rationale,
                confidence=args.confidence,
                inferred=args.inferred,
            )
        )
        return 0

    if args.command == "list-relations":
        output(
            {
                "items": planning.list_relations(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "synthesize-plan":
        output(
            planning.synthesize(
                project_id=args.project_id,
                allow_open_research=(
                    args.allow_open_research
                ),
            )
        )
        return 0

    if args.command == "list-plans":
        output(
            {
                "items": planning.list_plans(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "plan":
        output(
            planning.get_plan(
                args.plan_id
            )
        )
        return 0

    if args.command == "verify-plan":
        output(
            planning.verify_plan(
                args.plan_id
            )
        )
        return 0

'''

    cli = cli.replace(
        marker,
        commands + marker,
        1,
    )

update(
    "src/specgraph_foundry/cli.py",
    cli,
)


readme_path = ROOT / "README.md"
readme = readme_path.read_text(encoding="utf-8")

if "## Authority and execution planning" not in readme:
    section = dedent(
        r'''

        ## Authority and execution planning

        The planning backend provides:

        - typed atomic-requirement relationships;
        - dependency-cycle rejection;
        - authority graph generation;
        - contract, implementation, and verification stages;
        - cross-requirement dependency ordering;
        - unresolved-research readiness blocking;
        - deterministic plan fingerprints;
        - idempotent plan synthesis;
        - stored structural verification findings.

        ```bash
        python -m specgraph_foundry add-relation \
          PROJECT_ID \
          DEPENDENT_ATOM_ID \
          REQUIRED_ATOM_ID \
          REQUIRES

        python -m specgraph_foundry synthesize-plan \
          PROJECT_ID

        python -m specgraph_foundry verify-plan \
          PLAN_ID
        ```
        '''
    )

    readme = (
        readme.rstrip()
        + "\n"
        + section.lstrip()
    )

    update("README.md", readme)

print("PLANNING INTEGRATION REPAIRED")
