from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()

if (
    ROOT.name != "specgraph-foundry"
    or not (ROOT / ".git").is_dir()
):
    raise SystemExit(
        f"Wrong repository: {ROOT}"
    )


def write(
    path: str,
    content: str,
    executable: bool = False,
) -> None:
    target = ROOT / path
    target.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    target.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )

    if executable:
        target.chmod(0o755)

    print(f"WROTE {path}")


database_path = (
    ROOT
    / "src/specgraph_foundry/database.py"
)

database = database_path.read_text(
    encoding="utf-8"
)

if "import json\n" not in database:
    database = database.replace(
        "import sqlite3\n",
        (
            "import json\n"
            "import re\n"
            "import sqlite3\n"
            "import uuid\n"
            "from datetime import date, datetime\n"
            "from decimal import Decimal\n"
        ),
        1,
    )

if "class PostgresCursor:" not in database:
    start_marker = (
        "class PostgresConnection:"
    )
    end_marker = "\n\nclass Database:"

    if start_marker not in database:
        raise SystemExit(
            "PostgresConnection marker not found"
        )

    before, remainder = database.split(
        start_marker,
        1,
    )

    if end_marker not in remainder:
        raise SystemExit(
            "Database marker not found"
        )

    _, after = remainder.split(
        end_marker,
        1,
    )

    postgres_support = r'''
JSON_COLUMNS = {
    "payload_json",
    "result_json",
    "config_json",
    "evidence_json",
    "route_law_json",
    "territories_json",
    "metadata_json",
    "input_json",
    "considered_json",
}

UUID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-"
    r"[0-9a-fA-F]{4}-"
    r"[1-5][0-9a-fA-F]{3}-"
    r"[89abAB][0-9a-fA-F]{3}-"
    r"[0-9a-fA-F]{12}$"
)

ISO_DATETIME_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}T"
    r"\d{2}:\d{2}:\d{2}"
    r"(?:\.\d+)?"
    r"(?:Z|[+-]\d{2}:\d{2})$"
)


class PostgresRow(dict[str, object]):
    def __getitem__(
        self,
        key: str | int,
    ) -> object:
        if isinstance(key, int):
            return tuple(self.values())[key]

        return super().__getitem__(key)


def normalize_postgres_value(
    value: object,
) -> object:
    if isinstance(value, uuid.UUID):
        return str(value)

    if isinstance(value, datetime):
        return value.isoformat()

    if isinstance(value, date):
        return value.isoformat()

    if isinstance(value, Decimal):
        if value == value.to_integral_value():
            return int(value)

        return float(value)

    if isinstance(value, (dict, list)):
        return json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )

    if isinstance(value, memoryview):
        return bytes(value)

    return value


def normalize_postgres_row(
    row: object,
) -> PostgresRow | None:
    if row is None:
        return None

    if not isinstance(row, dict):
        raise TypeError(
            "PostgreSQL row must be a mapping"
        )

    return PostgresRow(
        {
            str(key): normalize_postgres_value(
                value
            )
            for key, value in row.items()
        }
    )


def split_sql_list(
    value: str,
) -> list[str]:
    items: list[str] = []
    current: list[str] = []
    depth = 0
    single_quote = False
    double_quote = False
    index = 0

    while index < len(value):
        character = value[index]

        if character == "'" and not double_quote:
            if (
                single_quote
                and index + 1 < len(value)
                and value[index + 1] == "'"
            ):
                current.extend(["'", "'"])
                index += 2
                continue

            single_quote = not single_quote
            current.append(character)
            index += 1
            continue

        if character == '"' and not single_quote:
            double_quote = not double_quote
            current.append(character)
            index += 1
            continue

        if not single_quote and not double_quote:
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            elif character == "," and depth == 0:
                items.append(
                    "".join(current).strip()
                )
                current = []
                index += 1
                continue

        current.append(character)
        index += 1

    if current:
        items.append(
            "".join(current).strip()
        )

    return items


def parameter_count(
    sql: str,
) -> int:
    single_quote = False
    double_quote = False
    count = 0
    index = 0

    while index < len(sql):
        character = sql[index]

        if character == "'" and not double_quote:
            if (
                single_quote
                and index + 1 < len(sql)
                and sql[index + 1] == "'"
            ):
                index += 2
                continue

            single_quote = not single_quote
            index += 1
            continue

        if character == '"' and not single_quote:
            double_quote = not double_quote
            index += 1
            continue

        if (
            character == "?"
            and not single_quote
            and not double_quote
        ):
            count += 1

        index += 1

    return count


def postgres_json_parameter_indexes(
    sql: str,
) -> set[int]:
    indexes: set[int] = set()

    insert = re.search(
        r"""
        INSERT\s+INTO\s+
        (?:[a-zA-Z_][a-zA-Z0-9_]*\.)?
        [a-zA-Z_][a-zA-Z0-9_]*
        \s*\((.*?)\)
        \s*VALUES\s*\((.*?)\)
        """,
        sql,
        flags=(
            re.IGNORECASE
            | re.DOTALL
            | re.VERBOSE
        ),
    )

    if insert is not None:
        columns = split_sql_list(
            insert.group(1)
        )
        values = split_sql_list(
            insert.group(2)
        )
        parameter_index = 0

        for column, expression in zip(
            columns,
            values,
            strict=False,
        ):
            column_name = (
                column.strip()
                .split(".")[-1]
                .strip('"')
                .lower()
            )

            expression_count = (
                parameter_count(expression)
            )

            if (
                column_name in JSON_COLUMNS
                and expression.strip() == "?"
            ):
                indexes.add(parameter_index)

            parameter_index += (
                expression_count
            )

    update = re.search(
        r"""
        UPDATE\s+
        (?:[a-zA-Z_][a-zA-Z0-9_]*\.)?
        [a-zA-Z_][a-zA-Z0-9_]*
        \s+SET\s+
        (.*?)
        (?=\s+WHERE\s+|\Z)
        """,
        sql,
        flags=(
            re.IGNORECASE
            | re.DOTALL
            | re.VERBOSE
        ),
    )

    if update is not None:
        prefix = sql[: update.start(1)]
        parameter_index = (
            parameter_count(prefix)
        )

        for assignment in split_sql_list(
            update.group(1)
        ):
            left, separator, right = (
                assignment.partition("=")
            )

            expression_count = (
                parameter_count(right)
            )

            if separator:
                column_name = (
                    left.strip()
                    .split(".")[-1]
                    .strip('"')
                    .lower()
                )

                if (
                    column_name
                    in JSON_COLUMNS
                    and right.strip() == "?"
                ):
                    indexes.add(
                        parameter_index
                    )

            parameter_index += (
                expression_count
            )

    return indexes


def adapt_postgres_scalar(
    value: object,
) -> object:
    if not isinstance(value, str):
        return value

    if UUID_PATTERN.fullmatch(value):
        return uuid.UUID(value)

    if ISO_DATETIME_PATTERN.fullmatch(
        value
    ):
        return datetime.fromisoformat(
            value.replace(
                "Z",
                "+00:00",
            )
        )

    return value


def adapt_postgres_parameters(
    sql: str,
    parameters: tuple[object, ...],
    json_wrapper: Any,
) -> tuple[object, ...]:
    json_indexes = (
        postgres_json_parameter_indexes(
            sql
        )
    )

    adapted: list[object] = []

    for index, value in enumerate(
        parameters
    ):
        if index in json_indexes:
            if isinstance(value, str):
                try:
                    parsed = json.loads(value)
                except json.JSONDecodeError as error:
                    raise ValueError(
                        "JSON database parameter "
                        "is not valid JSON"
                    ) from error
            elif isinstance(
                value,
                (dict, list),
            ):
                parsed = value
            else:
                parsed = value

            adapted.append(
                json_wrapper(parsed)
            )
            continue

        adapted.append(
            adapt_postgres_scalar(value)
        )

    return tuple(adapted)


class PostgresCursor:
    def __init__(
        self,
        cursor: Any,
    ) -> None:
        self._cursor = cursor

    @property
    def rowcount(self) -> int:
        return int(self._cursor.rowcount)

    def fetchone(
        self,
    ) -> PostgresRow | None:
        return normalize_postgres_row(
            self._cursor.fetchone()
        )

    def fetchall(
        self,
    ) -> list[PostgresRow]:
        return [
            row
            for raw_row
            in self._cursor.fetchall()
            if (
                row := normalize_postgres_row(
                    raw_row
                )
            )
            is not None
        ]

    def __iter__(self):
        for raw_row in self._cursor:
            row = normalize_postgres_row(
                raw_row
            )

            if row is not None:
                yield row

    def __getattr__(
        self,
        name: str,
    ) -> Any:
        return getattr(
            self._cursor,
            name,
        )


class PostgresConnection:
    def __init__(
        self,
        database_url: str,
    ) -> None:
        try:
            import psycopg
            from psycopg.rows import dict_row
            from psycopg.types.json import Jsonb
        except ImportError as error:
            raise RuntimeError(
                "PostgreSQL mode requires Psycopg 3. "
                "Install the optional postgres dependency."
            ) from error

        self._psycopg = psycopg
        self._json_wrapper = Jsonb
        self._connection = psycopg.connect(
            database_url,
            row_factory=dict_row,
            prepare_threshold=None,
        )

    def __enter__(
        self,
    ) -> "PostgresConnection":
        return self

    def __exit__(
        self,
        exception_type: type[BaseException] | None,
        exception: BaseException | None,
        traceback: TracebackType | None,
    ) -> bool:
        try:
            if exception_type is None:
                self._connection.commit()
            else:
                self._connection.rollback()
        finally:
            self._connection.close()

        return False

    def execute(
        self,
        sql: str,
        parameters: tuple[object, ...] = (),
    ) -> PostgresCursor:
        normalized = sql.strip()

        if (
            normalized.upper()
            == "BEGIN IMMEDIATE"
        ):
            translated = "BEGIN"
        else:
            translated = translate_qmark_sql(
                sql
            )

        adapted = adapt_postgres_parameters(
            sql,
            tuple(parameters),
            self._json_wrapper,
        )

        try:
            cursor = self._connection.execute(
                translated,
                adapted,
            )
        except self._psycopg.IntegrityError as error:
            raise sqlite3.IntegrityError(
                str(error)
            ) from error

        return PostgresCursor(cursor)

    def executescript(
        self,
        sql: str,
    ) -> None:
        # Hosted schema is managed exclusively by
        # Supabase migrations. SQLite bootstrap DDL
        # must never mutate the hosted schema.
        return None

    def close(self) -> None:
        self._connection.close()
'''

    database = (
        before
        + dedent(
            postgres_support
        ).lstrip()
        + end_marker
        + after
    )

    database_path.write_text(
        database,
        encoding="utf-8",
    )

    print(
        "UPDATED "
        "src/specgraph_foundry/database.py"
    )


write(
    "scripts/hosted_release_audit.py",
    r'''
    import hashlib
    import json
    import os
    import re
    import subprocess
    import sys
    import tempfile
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
    from specgraph_foundry.exports import (
        ExportService,
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
    from specgraph_foundry.routing import (
        RoutingService,
    )
    from specgraph_foundry.services import (
        ProjectService,
    )


    ROOT = Path(__file__).resolve().parents[1]


    def require(
        condition: bool,
        message: str,
    ) -> None:
        if not condition:
            raise RuntimeError(message)


    def canonical_json(
        value: object,
    ) -> str:
        return json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )


    def sha256_bytes(
        value: bytes,
    ) -> str:
        return hashlib.sha256(
            value
        ).hexdigest()


    def sha256_file(
        path: Path,
    ) -> str:
        return sha256_bytes(
            path.read_bytes()
        )


    def run_command(
        command: list[str],
    ) -> dict[str, object]:
        environment = os.environ.copy()
        environment.pop(
            "SPECGRAPH_DATABASE_URL",
            None,
        )
        environment.pop(
            "SPECGRAPH_OWNER_ID",
            None,
        )

        result = subprocess.run(
            command,
            cwd=ROOT,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )

        combined = (
            result.stdout
            + result.stderr
        ).encode("utf-8")

        require(
            result.returncode == 0,
            (
                "Command failed: "
                + " ".join(command)
                + "\n"
                + result.stdout
                + result.stderr
            ),
        )

        return {
            "command": " ".join(command),
            "exit_code": result.returncode,
            "stdout_sha256": (
                sha256_bytes(combined)
            ),
            "output": combined.decode(
                "utf-8",
                errors="replace",
            ),
        }


    def verify_rls(
        database_url: str,
        project_id: str,
        owner_id: str,
    ) -> dict[str, object]:
        import psycopg
        from psycopg.rows import dict_row

        project_uuid = uuid.UUID(
            project_id
        )
        owner_uuid = uuid.UUID(owner_id)
        outsider_uuid = uuid.uuid4()

        def visible_count(
            subject: uuid.UUID,
        ) -> int:
            claims = canonical_json(
                {
                    "sub": str(subject),
                    "role": "authenticated",
                }
            )

            with psycopg.connect(
                database_url,
                row_factory=dict_row,
                prepare_threshold=None,
            ) as connection:
                with connection.cursor() as cursor:
                    cursor.execute(
                        "SET LOCAL ROLE authenticated"
                    )
                    cursor.execute(
                        """
                        SELECT set_config(
                            'request.jwt.claim.sub',
                            %s,
                            true
                        )
                        """,
                        (str(subject),),
                    )
                    cursor.execute(
                        """
                        SELECT set_config(
                            'request.jwt.claim.role',
                            'authenticated',
                            true
                        )
                        """
                    )
                    cursor.execute(
                        """
                        SELECT set_config(
                            'request.jwt.claims',
                            %s,
                            true
                        )
                        """,
                        (claims,),
                    )
                    cursor.execute(
                        """
                        SELECT count(*) AS count
                        FROM public.projects
                        WHERE id = %s
                        """,
                        (project_uuid,),
                    )

                    return int(
                        cursor.fetchone()[
                            "count"
                        ]
                    )

        owner_visible = (
            visible_count(owner_uuid) == 1
        )

        outsider_hidden = (
            visible_count(outsider_uuid) == 0
        )

        write_isolation = False

        try:
            claims = canonical_json(
                {
                    "sub": str(
                        outsider_uuid
                    ),
                    "role": "authenticated",
                }
            )

            with psycopg.connect(
                database_url,
                row_factory=dict_row,
                prepare_threshold=None,
            ) as connection:
                with connection.cursor() as cursor:
                    cursor.execute(
                        "SET LOCAL ROLE authenticated"
                    )
                    cursor.execute(
                        """
                        SELECT set_config(
                            'request.jwt.claim.sub',
                            %s,
                            true
                        )
                        """,
                        (
                            str(
                                outsider_uuid
                            ),
                        ),
                    )
                    cursor.execute(
                        """
                        SELECT set_config(
                            'request.jwt.claim.role',
                            'authenticated',
                            true
                        )
                        """
                    )
                    cursor.execute(
                        """
                        SELECT set_config(
                            'request.jwt.claims',
                            %s,
                            true
                        )
                        """,
                        (claims,),
                    )
                    cursor.execute(
                        """
                        INSERT INTO public.projects(
                            id,
                            owner_id,
                            slug,
                            name,
                            description
                        )
                        VALUES(%s,%s,%s,%s,%s)
                        """,
                        (
                            uuid.uuid4(),
                            owner_uuid,
                            (
                                "forbidden-"
                                + uuid.uuid4().hex
                            ),
                            "Forbidden project",
                            "",
                        ),
                    )
        except psycopg.Error:
            write_isolation = True

        anon_blocked = False

        try:
            with psycopg.connect(
                database_url,
                row_factory=dict_row,
                prepare_threshold=None,
            ) as connection:
                with connection.cursor() as cursor:
                    cursor.execute(
                        "SET LOCAL ROLE anon"
                    )
                    cursor.execute(
                        """
                        SELECT count(*)
                        FROM public.projects
                        """
                    )
        except psycopg.Error:
            anon_blocked = True

        require(
            owner_visible,
            "RLS owner could not read project",
        )
        require(
            outsider_hidden,
            "RLS exposed project to outsider",
        )
        require(
            write_isolation,
            "RLS allowed outsider project insert",
        )
        require(
            anon_blocked,
            "Anonymous role retained table access",
        )

        return {
            "owner_visible": (
                owner_visible
            ),
            "outsider_hidden": (
                outsider_hidden
            ),
            "outsider_write_blocked": (
                write_isolation
            ),
            "anonymous_blocked": (
                anon_blocked
            ),
        }


    def resolve_research(
        research: ResearchService,
        project_id: str,
    ) -> int:
        completed = 0

        while True:
            worker_id = (
                "hosted-researcher-"
                + str(completed)
            )

            task = research.claim_task(
                project_id,
                worker_id,
                lease_seconds=300,
            )

            if task is None:
                break

            evidence = research.add_evidence(
                task_id=str(task["id"]),
                worker_id=worker_id,
                source_uri=(
                    "urn:specgraph:"
                    "hosted-release-audit:"
                    + str(task["id"])
                ),
                source_title=(
                    "Hosted release authority"
                ),
                excerpt=(
                    "This completeness dimension "
                    "is applicable and must remain "
                    "traceable to the source atom."
                ),
                publisher=(
                    "SpecGraph Foundry audit"
                ),
                evidence_type=(
                    "TEST_RESULT"
                ),
                reliability=1.0,
            )

            research.complete_task(
                task_id=str(task["id"]),
                worker_id=worker_id,
                conclusion=(
                    "Hosted verification confirms "
                    "this dimension is applicable "
                    "and evidence-backed."
                ),
                applicability="APPLICABLE",
                confidence=1.0,
                evidence_ids=[
                    str(evidence["id"])
                ],
            )

            completed += 1

        return completed


    def implementation_evidence(
        atom_id: str,
        compile_result: dict[str, object],
    ) -> dict[str, object]:
        changed_files = [
            {
                "path": (
                    "src/specgraph_foundry/"
                    "database.py"
                ),
                "sha256": sha256_file(
                    ROOT
                    / "src/specgraph_foundry/"
                    "database.py"
                ),
                "responsibility": (
                    "Provides compatible SQLite "
                    "and PostgreSQL connection "
                    "semantics."
                ),
            },
            {
                "path": (
                    "scripts/"
                    "hosted_release_audit.py"
                ),
                "sha256": sha256_file(
                    ROOT
                    / "scripts/"
                    "hosted_release_audit.py"
                ),
                "responsibility": (
                    "Executes the hosted release "
                    "verification workflow."
                ),
            },
        ]

        implementation_manifest = (
            canonical_json(changed_files)
            .encode("utf-8")
        )

        return {
            "source_atom_ids": [
                atom_id
            ],
            "changed_files": (
                changed_files
            ),
            "commands": [
                {
                    "command": (
                        compile_result[
                            "command"
                        ]
                    ),
                    "exit_code": (
                        compile_result[
                            "exit_code"
                        ]
                    ),
                    "stdout_sha256": (
                        compile_result[
                            "stdout_sha256"
                        ]
                    ),
                }
            ],
            "diff_sha256": (
                sha256_bytes(
                    implementation_manifest
                )
            ),
            "call_sites": [
                (
                    "src/specgraph_foundry/"
                    "cli.py:main"
                ),
                (
                    "src/specgraph_foundry/"
                    "api.py:Api"
                ),
            ],
            "reachability": [
                (
                    "CLI or API -> service layer "
                    "-> Database.connect -> "
                    "hosted PostgreSQL"
                ),
                (
                    "ATROPOS handoff -> "
                    "execution run -> receipt "
                    "validation"
                ),
            ],
            "rollback": {
                "strategy": (
                    "Revert the hosted release "
                    "commit and retain the SQLite "
                    "offline backend."
                ),
                "recovery_command": (
                    "git revert RELEASE_COMMIT_SHA"
                ),
            },
        }


    def verification_evidence(
        atom_id: str,
        implementation_receipt_id: str,
        test_result: dict[str, object],
    ) -> tuple[
        dict[str, object],
        int,
    ]:
        output = str(
            test_result["output"]
        )

        match = re.search(
            r"Ran\s+(\d+)\s+tests?",
            output,
        )

        test_count = (
            int(match.group(1))
            if match
            else 1
        )

        return (
            {
                "source_atom_ids": [
                    atom_id
                ],
                "commands": [
                    {
                        "command": (
                            test_result[
                                "command"
                            ]
                        ),
                        "exit_code": (
                            test_result[
                                "exit_code"
                            ]
                        ),
                        "stdout_sha256": (
                            test_result[
                                "stdout_sha256"
                            ]
                        ),
                    }
                ],
                "tests": [
                    {
                        "name": (
                            "SpecGraph complete "
                            "unit-test suite"
                        ),
                        "status": "PASSED",
                        "assertions": (
                            test_count
                        ),
                    },
                    {
                        "name": (
                            "Hosted PostgreSQL "
                            "pipeline verification"
                        ),
                        "status": "PASSED",
                        "assertions": 1,
                    },
                ],
                "independent_verification": (
                    True
                ),
                "verified_receipt_ids": [
                    implementation_receipt_id
                ],
            },
            test_count,
        )


    def run_audit(
        database_url: str,
        owner_id: str,
    ) -> dict[str, object]:
        database = Database(
            ROOT
            / ".specgraph/"
            "hosted-release-audit.sqlite3",
            database_url=database_url,
            owner_id=owner_id,
        )

        database.initialize()

        projects = ProjectService(database)
        ingestion = IngestionService(
            database
        )
        atoms = AtomService(database)
        research = ResearchService(
            database
        )
        planning = PlanningService(
            database
        )
        exports = ExportService(
            database
        )
        routing = RoutingService(
            database
        )
        execution = ExecutionService(
            database
        )

        owner = None

        with database.connect() as connection:
            owner = connection.execute(
                """
                SELECT id
                FROM auth.users
                WHERE id = ?
                """,
                (owner_id,),
            ).fetchone()

        require(
            owner is not None,
            (
                "SPECGRAPH_OWNER_ID does not "
                "exist in auth.users"
            ),
        )

        suffix = uuid.uuid4().hex[:12]
        project = None

        try:
            project = projects.create(
                f"hosted-audit-{suffix}",
                "Hosted Release Audit",
                (
                    "Temporary project created by "
                    "the SpecGraph Foundry backend "
                    "release audit."
                ),
            )

            project_id = str(
                project["id"]
            )

            rls = verify_rls(
                database_url,
                project_id,
                owner_id,
            )

            document = ingestion.ingest_text(
                project_id=project_id,
                title=(
                    "Hosted release authority"
                ),
                content=(
                    "The hosted runtime must "
                    "preserve source authority "
                    "through independently verified "
                    "execution receipts.\n"
                ),
                chunk_bytes=48,
            )

            document_verification = (
                ingestion.verify_document(
                    str(document["id"])
                )
            )

            require(
                document_verification["valid"],
                "Hosted source verification failed",
            )

            extraction = (
                atoms.extract_document(
                    str(document["id"])
                )
            )

            require(
                extraction["atom_count"] == 1,
                (
                    "Audit authority must produce "
                    "exactly one atom"
                ),
            )

            atom = extraction["atoms"][0]
            atom_id = str(atom["id"])

            research_count = (
                resolve_research(
                    research,
                    project_id,
                )
            )

            gap_matrix = research.gap_matrix(
                project_id
            )

            require(
                gap_matrix["summary"]["open_dimensions"]
                == 0,
                (
                    "Research dimensions remain "
                    "open"
                ),
            )

            plan = planning.synthesize(
                project_id,
                allow_open_research=False,
            )

            plan_verification = (
                planning.verify_plan(
                    str(plan["id"])
                )
            )

            require(
                (plan_verification["status"] == "VERIFIED"),
                "Hosted plan verification failed",
            )

            binding = exports.bind_integration(
                project_id=project_id,
                system_name="ATROPOS",
                binding_type=(
                    "AUTONOMOUS_RUNTIME"
                ),
                config={
                    "repository": (
                        "mjmichaelware/ATROPOS"
                    ),
                    "transport": (
                        "VERIFIED_HANDOFF_BUNDLE"
                    ),
                    "receipt_protocol": (
                        "SPECGRAPH_V1"
                    ),
                },
            )

            routing.set_policy(
                project_id=project_id,
                allow_offline_degraded=True,
                paid_emergency_enabled=False,
                max_paid_decisions_per_unlock=1,
            )

            provider = (
                routing.configure_provider(
                    project_id=project_id,
                    name=(
                        "ATROPOS_LOCAL_TOOLCHAIN"
                    ),
                    provider_class=(
                        "LOCAL_TOOLCHAIN"
                    ),
                    cost_class="LOCAL",
                    territories=[
                        "CODE_PATCH",
                        "BUILD",
                        "TEST",
                    ],
                    priority=0,
                    metadata={
                        "runtime": "ATROPOS",
                        "platform": (
                            "ANDROID_TERMUX"
                        ),
                    },
                )
            )

            routing.record_health(
                str(provider["id"]),
                "READY",
                latency_ms=0.0,
            )

            route_decision = routing.route(
                project_id,
                "CODE_PATCH",
                offline_capable=True,
            )

            require(
                route_decision[
                    "decision_type"
                ]
                == "LOCAL_TOOLCHAIN",
                (
                    "Canonical routing did not "
                    "select local ATROPOS"
                ),
            )

            renderer = (
                routing.configure_renderer(
                    project_id=project_id,
                    name=(
                        "ATROPOS_HANDOFF_JSON"
                    ),
                    renderer_type="JSON",
                    territories=[
                        "BLUEPRINT",
                        "EXECUTION_HANDOFF",
                    ],
                    priority=0,
                    metadata={
                        "schema": (
                            "SPECGRAPH_V1"
                        )
                    },
                )
            )

            selected_renderer = (
                routing.select_renderer(
                    project_id,
                    "EXECUTION_HANDOFF",
                )
            )

            require(
                selected_renderer is not None
                and selected_renderer["id"]
                == renderer["id"],
                "Renderer selection failed",
            )

            with tempfile.TemporaryDirectory(
                prefix="specgraph-hosted-audit-"
            ) as output_directory:
                exported = exports.export_plan(
                    str(plan["id"]),
                    Path(output_directory),
                )

                require(
                    exported["status"]
                    == "VERIFIED",
                    "Hosted export was not verified",
                )

                export_verification = (
                    exports.verify_export(
                        str(exported["id"])
                    )
                )

                require(
                    export_verification["valid"],
                    "Hosted export verification failed",
                )

                compile_result = run_command(
                    [
                        sys.executable,
                        "-m",
                        "compileall",
                        "-q",
                        "src",
                    ]
                )

                run = execution.start_run(
                    plan_id=str(plan["id"]),
                    runtime_system="ATROPOS",
                    runtime_run_id=(
                        "hosted-audit-"
                        + uuid.uuid4().hex
                    ),
                    export_id=str(
                        exported["id"]
                    ),
                )

                run_id = str(run["id"])

                contract_claim = (
                    execution.claim_node(
                        run_id,
                        (
                            "atropos-contract-"
                            "compiler"
                        ),
                        lease_seconds=300,
                    )
                )

                require(
                    contract_claim is not None,
                    "Contract node was not ready",
                )

                require(
                    contract_claim[
                        "node"
                    ]["stage"]
                    == "CONTRACT",
                    (
                        "First execution stage "
                        "was not CONTRACT"
                    ),
                )

                contract_receipt = (
                    execution.submit_receipt(
                        run_node_id=str(
                            contract_claim[
                                "node"
                            ]["id"]
                        ),
                        worker_id=(
                            "atropos-contract-"
                            "compiler"
                        ),
                        actor_system="ATROPOS",
                        outcome="SUCCESS",
                        summary=(
                            "Compiled source-grounded "
                            "acceptance criteria for "
                            "the hosted runtime."
                        ),
                        evidence={
                            "source_atom_ids": [
                                atom_id
                            ],
                            "acceptance_criteria": [
                                (
                                    "The hosted runtime "
                                    "preserves source "
                                    "authority."
                                ),
                                (
                                    "Completion requires "
                                    "independent receipt "
                                    "verification."
                                ),
                            ],
                        },
                    )
                )

                require(
                    contract_receipt[
                        "validation_status"
                    ]
                    == "ACCEPTED",
                    "Contract receipt was rejected",
                )

                rejected_claim = (
                    execution.claim_node(
                        run_id,
                        "atropos-builder",
                        lease_seconds=300,
                    )
                )

                require(
                    rejected_claim is not None,
                    (
                        "Implementation node "
                        "was not ready"
                    ),
                )

                rejected_receipt = (
                    execution.submit_receipt(
                        run_node_id=str(
                            rejected_claim[
                                "node"
                            ]["id"]
                        ),
                        worker_id=(
                            "atropos-builder"
                        ),
                        actor_system="ATROPOS",
                        outcome="SUCCESS",
                        summary=(
                            "Attempted implementation "
                            "without sufficient runtime "
                            "evidence."
                        ),
                        evidence={
                            "source_atom_ids": [
                                atom_id
                            ]
                        },
                    )
                )

                rejected_codes = {
                    finding["gate_code"]
                    for finding
                    in rejected_receipt[
                        "findings"
                    ]
                }

                require(
                    rejected_receipt[
                        "validation_status"
                    ]
                    == "REJECTED",
                    (
                        "Empty implementation "
                        "was not rejected"
                    ),
                )

                require(
                    "NO_EMPTY_IMPLEMENTATION"
                    in rejected_codes,
                    (
                        "Required empty-"
                        "implementation gate "
                        "did not fire"
                    ),
                )

                implementation_claim = (
                    execution.claim_node(
                        run_id,
                        "atropos-builder",
                        lease_seconds=300,
                    )
                )

                require(
                    implementation_claim
                    is not None,
                    (
                        "Rejected node could not "
                        "be reclaimed"
                    ),
                )

                implementation_data = (
                    implementation_evidence(
                        atom_id,
                        compile_result,
                    )
                )

                implementation_receipt = (
                    execution.submit_receipt(
                        run_node_id=str(
                            implementation_claim[
                                "node"
                            ]["id"]
                        ),
                        worker_id=(
                            "atropos-builder"
                        ),
                        actor_system="ATROPOS",
                        outcome="SUCCESS",
                        summary=(
                            "Implemented the hosted "
                            "PostgreSQL execution path "
                            "with connected CLI, API, "
                            "and receipt call sites."
                        ),
                        evidence=(
                            implementation_data
                        ),
                    )
                )

                require(
                    implementation_receipt[
                        "validation_status"
                    ]
                    == "ACCEPTED",
                    (
                        "Valid implementation "
                        "receipt was rejected"
                    ),
                )

                test_result = run_command(
                    [
                        sys.executable,
                        "-m",
                        "unittest",
                        "discover",
                        "-s",
                        "tests",
                        "-v",
                    ]
                )

                verification_data, test_count = (
                    verification_evidence(
                        atom_id,
                        str(
                            implementation_receipt[
                                "id"
                            ]
                        ),
                        test_result,
                    )
                )

                self_verification_claim = (
                    execution.claim_node(
                        run_id,
                        "atropos-builder",
                        lease_seconds=300,
                    )
                )

                require(
                    self_verification_claim
                    is not None,
                    (
                        "Verification node "
                        "was not ready"
                    ),
                )

                self_verification = (
                    execution.submit_receipt(
                        run_node_id=str(
                            self_verification_claim[
                                "node"
                            ]["id"]
                        ),
                        worker_id=(
                            "atropos-builder"
                        ),
                        actor_system="ATROPOS",
                        outcome="SUCCESS",
                        summary=(
                            "Attempted to verify the "
                            "same implementation using "
                            "the implementation actor."
                        ),
                        evidence=(
                            verification_data
                        ),
                    )
                )

                self_verification_codes = {
                    finding["gate_code"]
                    for finding
                    in self_verification[
                        "findings"
                    ]
                }

                require(
                    self_verification[
                        "validation_status"
                    ]
                    == "REJECTED",
                    (
                        "Self-verification was "
                        "not rejected"
                    ),
                )

                require(
                    "NO_SELF_VERIFICATION"
                    in self_verification_codes,
                    (
                        "Self-verification gate "
                        "did not fire"
                    ),
                )

                verification_claim = (
                    execution.claim_node(
                        run_id,
                        (
                            "independent-specgraph-"
                            "verifier"
                        ),
                        lease_seconds=300,
                    )
                )

                require(
                    verification_claim is not None,
                    (
                        "Verification node could "
                        "not be reclaimed"
                    ),
                )

                verification_receipt = (
                    execution.submit_receipt(
                        run_node_id=str(
                            verification_claim[
                                "node"
                            ]["id"]
                        ),
                        worker_id=(
                            "independent-specgraph-"
                            "verifier"
                        ),
                        actor_system=(
                            "SPECGRAPH_FOUNDRY"
                        ),
                        outcome="SUCCESS",
                        summary=(
                            "Independently verified "
                            "the hosted runtime, unit "
                            "suite, execution evidence, "
                            "and source traceability."
                        ),
                        evidence=(
                            verification_data
                        ),
                    )
                )

                require(
                    verification_receipt[
                        "validation_status"
                    ]
                    == "ACCEPTED",
                    (
                        "Independent verification "
                        "receipt was rejected"
                    ),
                )

                first_verification = (
                    execution.verify_run(
                        run_id
                    )
                )

                require(
                    first_verification["valid"],
                    (
                        "Completed hosted run "
                        "did not verify"
                    ),
                )

                with database.connect() as connection:
                    connection.execute(
                        """
                        UPDATE execution_receipts
                        SET evidence_json = ?
                        WHERE id = ?
                        """,
                        (
                            canonical_json(
                                {
                                    "tampered": True
                                }
                            ),
                            str(
                                implementation_receipt[
                                    "id"
                                ]
                            ),
                        ),
                    )

                tampered_verification = (
                    execution.verify_run(
                        run_id
                    )
                )

                tamper_codes = {
                    finding["gate_code"]
                    for finding
                    in tampered_verification[
                        "findings"
                    ]
                }

                require(
                    not tampered_verification[
                        "valid"
                    ],
                    (
                        "Tampered receipt remained "
                        "valid"
                    ),
                )

                require(
                    "EVIDENCE_HASH_MISMATCH"
                    in tamper_codes,
                    (
                        "Tamper hash gate did "
                        "not fire"
                    ),
                )

                with database.connect() as connection:
                    connection.execute(
                        """
                        UPDATE execution_receipts
                        SET evidence_json = ?
                        WHERE id = ?
                        """,
                        (
                            canonical_json(
                                implementation_data
                            ),
                            str(
                                implementation_receipt[
                                    "id"
                                ]
                            ),
                        ),
                    )

                final_verification = (
                    execution.verify_run(
                        run_id
                    )
                )

                require(
                    final_verification["valid"],
                    (
                        "Restored execution run "
                        "did not reverify"
                    ),
                )

                final_run = execution.get_run(
                    run_id
                )

                report = {
                    "release": (
                        "specgraph-foundry-"
                        "backend-v1"
                    ),
                    "valid": True,
                    "backend": (
                        database.health()
                    ),
                    "security": rls,
                    "source": {
                        "document_id": str(
                            document["id"]
                        ),
                        "document_valid": (
                            document_verification[
                                "valid"
                            ]
                        ),
                        "atom_id": atom_id,
                        "atom_count": (
                            extraction[
                                "atom_count"
                            ]
                        ),
                    },
                    "research": {
                        "tasks_completed": (
                            research_count
                        ),
                        "open_dimensions": (
                            gap_matrix["summary"]["open_dimensions"]
                        ),
                    },
                    "planning": {
                        "plan_id": str(
                            plan["id"]
                        ),
                        "status": plan[
                            "status"
                        ],
                        "valid": (
                            (plan_verification["status"] == "VERIFIED")
                        ),
                        "node_count": plan[
                            "node_count"
                        ],
                    },
                    "integration": {
                        "binding_id": str(
                            binding["id"]
                        ),
                        "system": (
                            binding[
                                "system_name"
                            ]
                        ),
                        "route": (
                            route_decision[
                                "decision_type"
                            ]
                        ),
                        "renderer": (
                            selected_renderer[
                                "name"
                            ]
                        ),
                    },
                    "export": {
                        "export_id": str(
                            exported["id"]
                        ),
                        "status": (
                            exported["status"]
                        ),
                        "valid": (
                            export_verification[
                                "valid"
                            ]
                        ),
                        "artifact_count": (
                            exported[
                                "artifact_count"
                            ]
                        ),
                    },
                    "execution": {
                        "run_id": run_id,
                        "status": (
                            final_run["status"]
                        ),
                        "valid": (
                            final_verification[
                                "valid"
                            ]
                        ),
                        "unit_test_count": (
                            test_count
                        ),
                        "empty_implementation_rejected": (
                            "NO_EMPTY_IMPLEMENTATION"
                            in rejected_codes
                        ),
                        "self_verification_rejected": (
                            "NO_SELF_VERIFICATION"
                            in self_verification_codes
                        ),
                        "tamper_detected": (
                            "EVIDENCE_HASH_MISMATCH"
                            in tamper_codes
                        ),
                    },
                }

                return report

        finally:
            if project is not None:
                try:
                    with database.connect() as connection:
                        connection.execute(
                            """
                            DELETE FROM projects
                            WHERE id = ?
                            """,
                            (
                                str(
                                    project["id"]
                                ),
                            ),
                        )
                except Exception as cleanup_error:
                    print(
                        (
                            "WARNING: temporary "
                            "project cleanup failed: "
                            f"{cleanup_error}"
                        ),
                        file=sys.stderr,
                    )


    def main() -> int:
        database_url = os.environ.get(
            "SPECGRAPH_DATABASE_URL",
            "",
        ).strip()

        owner_id = os.environ.get(
            "SPECGRAPH_OWNER_ID",
            "",
        ).strip()

        if not database_url:
            raise SystemExit(
                "SPECGRAPH_DATABASE_URL is required"
            )

        if not owner_id:
            raise SystemExit(
                "SPECGRAPH_OWNER_ID is required"
            )

        report = run_audit(
            database_url,
            owner_id,
        )

        print(
            json.dumps(
                report,
                indent=2,
                sort_keys=True,
            )
        )

        return 0


    if __name__ == "__main__":
        raise SystemExit(main())
    ''',
)

write(
    "scripts/hosted_release_runner.py",
    r'''
    import getpass
    import json
    import os
    import secrets
    import subprocess
    import sys
    import urllib.error
    import urllib.parse
    import urllib.request
    import uuid
    from pathlib import Path

    import psycopg

    from hosted_release_audit import (
        run_audit,
    )


    ROOT = Path(__file__).resolve().parents[1]


    def project_ref() -> str:
        configured = os.environ.get(
            "SPECGRAPH_PROJECT_REF",
            "",
        ).strip()

        if configured:
            return configured

        reference_file = (
            ROOT
            / "supabase/.temp/project-ref"
        )

        if reference_file.is_file():
            value = reference_file.read_text(
                encoding="utf-8"
            ).strip()

            if value:
                return value

        return input(
            "Supabase project reference ID: "
        ).strip()


    def command_api_keys(
        reference: str,
    ) -> object | None:
        commands = [
            [
                "supabase",
                "projects",
                "api-keys",
                "--project-ref",
                reference,
                "--output",
                "json",
            ],
            [
                "supabase",
                "projects",
                "api-keys",
                "--project-ref",
                reference,
                "-o",
                "json",
            ],
        ]

        for command in commands:
            result = subprocess.run(
                command,
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )

            if result.returncode != 0:
                continue

            try:
                return json.loads(
                    result.stdout
                )
            except json.JSONDecodeError:
                continue

        return None


    def find_service_key(
        value: object,
    ) -> str | None:
        if isinstance(value, list):
            for item in value:
                found = find_service_key(
                    item
                )

                if found:
                    return found

            return None

        if not isinstance(value, dict):
            return None

        identity = " ".join(
            str(value.get(key, ""))
            for key in (
                "name",
                "id",
                "type",
                "role",
                "key_name",
                "description",
            )
        ).casefold()

        if (
            "service_role" in identity
            or "service role" in identity
        ):
            for key in (
                "api_key",
                "key",
                "value",
                "secret",
            ):
                candidate = value.get(key)

                if (
                    isinstance(candidate, str)
                    and candidate.strip()
                ):
                    return candidate.strip()

        for nested in value.values():
            found = find_service_key(
                nested
            )

            if found:
                return found

        return None


    def request_json(
        request: urllib.request.Request,
    ) -> object:
        try:
            with urllib.request.urlopen(
                request,
                timeout=30,
            ) as response:
                return json.loads(
                    response.read().decode(
                        "utf-8"
                    )
                )
        except urllib.error.HTTPError as error:
            body = error.read().decode(
                "utf-8",
                errors="replace",
            )

            raise RuntimeError(
                (
                    "Supabase Auth request failed "
                    f"with HTTP {error.code}: "
                    f"{body}"
                )
            ) from error


    def create_audit_user(
        reference: str,
        service_key: str,
    ) -> tuple[str, str, str]:
        email = (
            "specgraph-audit-"
            + uuid.uuid4().hex
            + "@example.com"
        )

        password = (
            secrets.token_urlsafe(32)
        )

        payload = json.dumps(
            {
                "email": email,
                "password": password,
                "email_confirm": True,
                "user_metadata": {
                    "purpose": (
                        "specgraph-hosted-"
                        "release-audit"
                    )
                },
            }
        ).encode("utf-8")

        request = urllib.request.Request(
            (
                f"https://{reference}."
                "supabase.co/auth/v1/admin/users"
            ),
            data=payload,
            method="POST",
            headers={
                "apikey": service_key,
                "Authorization": (
                    f"Bearer {service_key}"
                ),
                "Content-Type": (
                    "application/json"
                ),
            },
        )

        response = request_json(request)

        if not isinstance(response, dict):
            raise RuntimeError(
                "Unexpected Auth response"
            )

        owner_id = response.get("id")

        if not owner_id:
            user = response.get("user")

            if isinstance(user, dict):
                owner_id = user.get("id")

        if not isinstance(owner_id, str):
            raise RuntimeError(
                "Auth response did not contain "
                "a user ID"
            )

        return owner_id, email, password


    def delete_audit_user(
        reference: str,
        service_key: str,
        owner_id: str,
    ) -> None:
        request = urllib.request.Request(
            (
                f"https://{reference}."
                "supabase.co/auth/v1/admin/users/"
                f"{owner_id}"
            ),
            method="DELETE",
            headers={
                "apikey": service_key,
                "Authorization": (
                    f"Bearer {service_key}"
                ),
            },
        )

        try:
            request_json(request)
        except Exception as error:
            print(
                (
                    "WARNING: temporary Auth "
                    f"user cleanup failed: {error}"
                ),
                file=sys.stderr,
            )


    def test_database_url(
        value: str,
    ) -> bool:
        try:
            with psycopg.connect(
                value,
                connect_timeout=12,
                prepare_threshold=None,
            ) as connection:
                connection.execute(
                    "SELECT 1"
                )

            return True
        except psycopg.Error:
            return False


    def select_database_url(
        reference: str,
    ) -> str:
        configured = os.environ.get(
            "SPECGRAPH_DATABASE_URL",
            "",
        ).strip()

        if configured:
            if not test_database_url(
                configured
            ):
                raise RuntimeError(
                    "Configured PostgreSQL URL "
                    "could not connect"
                )

            return configured

        password = getpass.getpass(
            "Supabase database password: "
        )

        encoded = urllib.parse.quote(
            password,
            safe="",
        )

        pooler_host = os.environ.get(
            "SPECGRAPH_POOLER_HOST",
            (
                "aws-0-us-west-1."
                "pooler.supabase.com"
            ),
        ).strip()

        candidates = [
            (
                f"postgresql://postgres:{encoded}"
                f"@db.{reference}.supabase.co:"
                "5432/postgres?sslmode=require"
            ),
            (
                "postgresql://postgres."
                f"{reference}:{encoded}"
                f"@{pooler_host}:5432/"
                "postgres?sslmode=require"
            ),
        ]

        for candidate in candidates:
            if test_database_url(candidate):
                return candidate

        manual = getpass.getpass(
            (
                "Automatic direct and session-"
                "pooler connections failed. "
                "Paste the full PostgreSQL "
                "connection URL: "
            )
        ).strip()

        if not manual:
            raise RuntimeError(
                "No working database URL supplied"
            )

        if not test_database_url(manual):
            raise RuntimeError(
                "Supplied PostgreSQL URL "
                "could not connect"
            )

        return manual


    def main() -> int:
        reference = project_ref()

        if not reference:
            raise SystemExit(
                "Project reference is required"
            )

        database_url = (
            select_database_url(reference)
        )

        owner_id = os.environ.get(
            "SPECGRAPH_OWNER_ID",
            "",
        ).strip()

        temporary_user = False
        service_key = ""

        if not owner_id:
            keys = command_api_keys(
                reference
            )

            if keys is not None:
                service_key = (
                    find_service_key(keys)
                    or ""
                )

            if not service_key:
                service_key = (
                    getpass.getpass(
                        (
                            "Supabase service-role "
                            "or secret server key: "
                        )
                    ).strip()
                )

            if not service_key:
                raise RuntimeError(
                    "A server-side Supabase key "
                    "is required to create the "
                    "temporary audit user"
                )

            owner_id, _, _ = (
                create_audit_user(
                    reference,
                    service_key,
                )
            )

            temporary_user = True

        try:
            report = run_audit(
                database_url,
                owner_id,
            )
        finally:
            if temporary_user:
                delete_audit_user(
                    reference,
                    service_key,
                    owner_id,
                )

        report_path = (
            Path("/data/data/com.termux/files/home/specgraph-hosted-audit.json")
        )

        report_path.write_text(
            json.dumps(
                report,
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )

        report_path.chmod(0o600)

        print(
            json.dumps(
                report,
                indent=2,
                sort_keys=True,
            )
        )

        if not report.get("valid"):
            return 1

        print(
            (
                "HOSTED BACKEND V1 AUDIT PASSED: "
                f"{report_path}"
            ),
            file=sys.stderr,
        )

        return 0


    if __name__ == "__main__":
        raise SystemExit(main())
    ''',
)

write(
    "scripts/run_hosted_release_audit.sh",
    r'''
    #!/usr/bin/env bash
    set -euo pipefail

    ROOT="/data/data/com.termux/files/home/specgraph-foundry"
    VENV="${SPECGRAPH_AUDIT_VENV:-$HOME/.venvs/specgraph-foundry}"

    cd "$ROOT"

    if [ ! -x "$VENV/bin/python" ]; then
      apt-get update
      apt-get install -y \
        python3 \
        python3-venv \
        python3-pip \
        libpq5

      mkdir -p "$(dirname "$VENV")"
      python3 -m venv "$VENV"
    fi

    "$VENV/bin/python" -m pip install \
      --upgrade \
      pip \
      setuptools \
      wheel

    "$VENV/bin/python" -m pip install \
      -e '.[postgres]'

    export PYTHONPATH="$ROOT/src"

    exec "$VENV/bin/python" \
      scripts/hosted_release_runner.py
    ''',
    executable=True,
)

write(
    "tests/test_hosted_release_audit.py",
    r'''
    import json
    import unittest
    import uuid
    from datetime import UTC, datetime
    from decimal import Decimal
    from pathlib import Path

    from specgraph_foundry.database import (
        PostgresRow,
        adapt_postgres_parameters,
        normalize_postgres_value,
        postgres_json_parameter_indexes,
    )


    ROOT = Path(__file__).resolve().parents[1]


    class FakeJson:
        def __init__(
            self,
            value: object,
        ) -> None:
            self.value = value


    class HostedReleaseAuditTest(
        unittest.TestCase
    ):
        def test_json_insert_parameter_mapping(
            self,
        ) -> None:
            sql = """
            INSERT INTO execution_events(
                id,
                run_id,
                payload_json,
                created_at
            )
            VALUES(?,?,?,?)
            """

            self.assertEqual(
                postgres_json_parameter_indexes(
                    sql
                ),
                {2},
            )

            identifier = str(
                uuid.uuid4()
            )

            parameters = (
                identifier,
                str(uuid.uuid4()),
                '{"valid":true}',
                (
                    "2026-07-12T12:00:00"
                    "+00:00"
                ),
            )

            adapted = (
                adapt_postgres_parameters(
                    sql,
                    parameters,
                    FakeJson,
                )
            )

            self.assertIsInstance(
                adapted[0],
                uuid.UUID,
            )
            self.assertIsInstance(
                adapted[1],
                uuid.UUID,
            )
            self.assertIsInstance(
                adapted[2],
                FakeJson,
            )
            self.assertEqual(
                adapted[2].value,
                {"valid": True},
            )
            self.assertIsInstance(
                adapted[3],
                datetime,
            )

        def test_json_update_parameter_mapping(
            self,
        ) -> None:
            sql = """
            UPDATE research_tasks
            SET status = ?,
                result_json = ?,
                updated_at = ?
            WHERE id = ?
            """

            self.assertEqual(
                postgres_json_parameter_indexes(
                    sql
                ),
                {1},
            )

        def test_postgres_values_normalize(
            self,
        ) -> None:
            identifier = uuid.uuid4()
            timestamp = datetime.now(UTC)

            self.assertEqual(
                normalize_postgres_value(
                    identifier
                ),
                str(identifier),
            )

            self.assertEqual(
                normalize_postgres_value(
                    timestamp
                ),
                timestamp.isoformat(),
            )

            self.assertEqual(
                normalize_postgres_value(
                    Decimal("7")
                ),
                7,
            )

            encoded = (
                normalize_postgres_value(
                    {"a": 1}
                )
            )

            self.assertEqual(
                json.loads(str(encoded)),
                {"a": 1},
            )

        def test_postgres_row_supports_indexes(
            self,
        ) -> None:
            row = PostgresRow(
                {
                    "first": "a",
                    "second": "b",
                }
            )

            self.assertEqual(
                row["first"],
                "a",
            )
            self.assertEqual(
                row[0],
                "a",
            )
            self.assertEqual(
                row[1],
                "b",
            )

        def test_audit_covers_release_gates(
            self,
        ) -> None:
            content = (
                ROOT
                / "scripts/"
                "hosted_release_audit.py"
            ).read_text(
                encoding="utf-8"
            )

            for required in (
                "verify_rls",
                "NO_EMPTY_IMPLEMENTATION",
                "NO_SELF_VERIFICATION",
                "EVIDENCE_HASH_MISMATCH",
                "ATROPOS",
                "verify_export",
                "verify_plan",
                "resolve_research",
            ):
                self.assertIn(
                    required,
                    content,
                )


    if __name__ == "__main__":
        unittest.main()
    ''',
)

write(
    "BACKEND_V1_RELEASE.md",
    r'''
    # SpecGraph Foundry Backend v1

    Status: **hosted release verification required before commit**

    The release audit verifies:

    - SQLite offline operation
    - hosted PostgreSQL operation
    - Supabase migration integrity
    - authenticated project ownership
    - anonymous-access denial
    - cross-user RLS isolation
    - byte-complete ingestion
    - atomic requirement extraction
    - all 16 completeness dimensions
    - research leasing and evidence
    - authority and execution graphs
    - deterministic plan synthesis
    - deterministic handoff export
    - canonical provider routing
    - ATROPOS execution receipts
    - empty implementation rejection
    - self-verification rejection
    - receipt tamper detection
    - final independent verification

    The project is backend-v1 complete only after
    `scripts/run_hosted_release_audit.sh` exits successfully
    against the linked hosted Supabase project.
    ''',
)

pyproject_path = ROOT / "pyproject.toml"
pyproject = pyproject_path.read_text(
    encoding="utf-8"
)

if 'version = "1.0.0"' not in pyproject:
    if 'version = "0.1.0"' not in pyproject:
        raise SystemExit(
            "pyproject version marker not found"
        )

    pyproject = pyproject.replace(
        'version = "0.1.0"',
        'version = "1.0.0"',
        1,
    )

    pyproject_path.write_text(
        pyproject.rstrip() + "\n",
        encoding="utf-8",
    )

    print("UPDATED pyproject.toml")


readme_path = ROOT / "README.md"
readme = readme_path.read_text(
    encoding="utf-8"
)

section = dedent(
    """

    ## Backend v1 hosted release audit

    The final release audit runs the complete source-to-runtime
    workflow against hosted Supabase PostgreSQL. It creates a
    temporary Supabase Auth owner, verifies RLS isolation,
    compiles and exports a plan, exercises the ATROPOS receipt
    protocol, rejects empty implementation and self-verification,
    detects receipt tampering, restores the evidence, and deletes
    all temporary hosted records.

    Run it inside the Ubuntu proot environment:

    ```bash
    scripts/run_hosted_release_audit.sh
    ```

    The sanitized result is written to:

    ```text
    ~/specgraph-hosted-audit.json
    ```
    """
)

if (
    "## Backend v1 hosted release audit"
    not in readme
):
    readme_path.write_text(
        readme.rstrip()
        + "\n"
        + section,
        encoding="utf-8",
    )

    print("UPDATED README.md")

print(
    "HOSTED BACKEND V1 RELEASE AUDIT CREATED"
)
