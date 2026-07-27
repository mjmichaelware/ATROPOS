from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong repository: {ROOT}")


DIRECT_POLICIES = {
    "source_documents": """
        public.specgraph_is_project_owner(project_id)
    """,
    "graphs": """
        public.specgraph_is_project_owner(project_id)
    """,
    "ingestion_runs": """
        public.specgraph_is_project_owner(project_id)
        and (
            document_id is null
            or public.specgraph_document_project_id(
                document_id
            ) = project_id
        )
    """,
    "extraction_runs": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_document_project_id(
            document_id
        ) = project_id
    """,
    "atoms": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_document_project_id(
            document_id
        ) = project_id
        and (
            section_id is null
            or public.specgraph_section_project_id(
                section_id
            ) = project_id
        )
    """,
    "research_tasks": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_atom_project_id(
            atom_id
        ) = project_id
    """,
    "research_evidence": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_task_project_id(
            task_id
        ) = project_id
        and public.specgraph_atom_project_id(
            atom_id
        ) = project_id
    """,
    "research_claims": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_task_project_id(
            task_id
        ) = project_id
        and public.specgraph_atom_project_id(
            atom_id
        ) = project_id
    """,
    "authority_relations": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_atom_project_id(
            from_atom_id
        ) = project_id
        and public.specgraph_atom_project_id(
            to_atom_id
        ) = project_id
    """,
    "plan_versions": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_graph_project_id(
            authority_graph_id
        ) = project_id
        and public.specgraph_graph_project_id(
            execution_graph_id
        ) = project_id
    """,
    "integration_bindings": """
        public.specgraph_is_project_owner(project_id)
    """,
    "exports": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_plan_project_id(
            plan_version_id
        ) = project_id
    """,
    "execution_runs": """
        public.specgraph_is_project_owner(project_id)
        and public.specgraph_plan_project_id(
            plan_version_id
        ) = project_id
        and (
            export_id is null
            or public.specgraph_export_project_id(
                export_id
            ) = project_id
        )
    """,
    "project_policies": """
        public.specgraph_is_project_owner(project_id)
    """,
    "provider_configs": """
        public.specgraph_is_project_owner(project_id)
    """,
    "renderer_configs": """
        public.specgraph_is_project_owner(project_id)
    """,
    "paid_route_unlocks": """
        public.specgraph_is_project_owner(project_id)
        and (
            provider_id is null
            or public.specgraph_provider_project_id(
                provider_id
            ) = project_id
        )
    """,
    "route_decisions": """
        public.specgraph_is_project_owner(project_id)
        and (
            selected_provider_id is null
            or public.specgraph_provider_project_id(
                selected_provider_id
            ) = project_id
        )
        and (
            paid_unlock_id is null
            or public.specgraph_paid_unlock_project_id(
                paid_unlock_id
            ) = project_id
        )
    """,
}


INDIRECT_POLICIES = {
    "graph_nodes": """
        public.specgraph_is_project_owner(
            public.specgraph_graph_project_id(
                graph_id
            )
        )
    """,
    "graph_edges": """
        public.specgraph_is_project_owner(
            public.specgraph_graph_project_id(
                graph_id
            )
        )
        and public.specgraph_graph_node_project_id(
            from_node_id
        ) = public.specgraph_graph_project_id(
            graph_id
        )
        and public.specgraph_graph_node_project_id(
            to_node_id
        ) = public.specgraph_graph_project_id(
            graph_id
        )
    """,
    "source_sections": """
        public.specgraph_is_project_owner(
            public.specgraph_document_project_id(
                document_id
            )
        )
    """,
    "source_chunks": """
        public.specgraph_is_project_owner(
            public.specgraph_document_project_id(
                document_id
            )
        )
        and (
            section_id is null
            or public.specgraph_section_project_id(
                section_id
            ) = public.specgraph_document_project_id(
                document_id
            )
        )
    """,
    "atom_dimensions": """
        public.specgraph_is_project_owner(
            public.specgraph_atom_project_id(
                atom_id
            )
        )
    """,
    "research_claim_evidence": """
        public.specgraph_is_project_owner(
            public.specgraph_claim_project_id(
                claim_id
            )
        )
        and public.specgraph_claim_project_id(
            claim_id
        ) = public.specgraph_evidence_project_id(
            evidence_id
        )
    """,
    "research_task_events": """
        public.specgraph_is_project_owner(
            public.specgraph_task_project_id(
                task_id
            )
        )
    """,
    "plan_node_bindings": """
        public.specgraph_is_project_owner(
            public.specgraph_plan_project_id(
                plan_version_id
            )
        )
        and public.specgraph_graph_node_project_id(
            graph_node_id
        ) = public.specgraph_plan_project_id(
            plan_version_id
        )
        and public.specgraph_atom_project_id(
            atom_id
        ) = public.specgraph_plan_project_id(
            plan_version_id
        )
    """,
    "plan_verification_findings": """
        public.specgraph_is_project_owner(
            public.specgraph_plan_project_id(
                plan_version_id
            )
        )
    """,
    "export_verification_findings": """
        public.specgraph_is_project_owner(
            public.specgraph_export_project_id(
                export_id
            )
        )
    """,
    "execution_run_nodes": """
        public.specgraph_is_project_owner(
            public.specgraph_execution_run_project_id(
                run_id
            )
        )
        and public.specgraph_graph_node_project_id(
            graph_node_id
        ) = public.specgraph_execution_run_project_id(
            run_id
        )
        and public.specgraph_atom_project_id(
            atom_id
        ) = public.specgraph_execution_run_project_id(
            run_id
        )
    """,
    "execution_attempts": """
        public.specgraph_is_project_owner(
            public.specgraph_execution_node_project_id(
                run_node_id
            )
        )
    """,
    "execution_receipts": """
        public.specgraph_is_project_owner(
            public.specgraph_execution_run_project_id(
                run_id
            )
        )
        and public.specgraph_execution_node_project_id(
            run_node_id
        ) = public.specgraph_execution_run_project_id(
            run_id
        )
        and public.specgraph_execution_attempt_project_id(
            attempt_id
        ) = public.specgraph_execution_run_project_id(
            run_id
        )
    """,
    "execution_validation_findings": """
        public.specgraph_is_project_owner(
            public.specgraph_execution_run_project_id(
                run_id
            )
        )
        and (
            run_node_id is null
            or public.specgraph_execution_node_project_id(
                run_node_id
            ) = public.specgraph_execution_run_project_id(
                run_id
            )
        )
        and (
            receipt_id is null
            or public.specgraph_execution_receipt_project_id(
                receipt_id
            ) = public.specgraph_execution_run_project_id(
                run_id
            )
        )
    """,
    "execution_events": """
        public.specgraph_is_project_owner(
            public.specgraph_execution_run_project_id(
                run_id
            )
        )
        and (
            run_node_id is null
            or public.specgraph_execution_node_project_id(
                run_node_id
            ) = public.specgraph_execution_run_project_id(
                run_id
            )
        )
    """,
    "provider_health_events": """
        public.specgraph_is_project_owner(
            public.specgraph_provider_project_id(
                provider_id
            )
        )
    """,
}


PROJECT_ID_FUNCTIONS = {
    "document": """
        select item.project_id
        from public.source_documents as item
        where item.id = target_id
    """,
    "section": """
        select document.project_id
        from public.source_sections as section
        join public.source_documents as document
          on document.id = section.document_id
        where section.id = target_id
    """,
    "graph": """
        select item.project_id
        from public.graphs as item
        where item.id = target_id
    """,
    "graph_node": """
        select graph.project_id
        from public.graph_nodes as node
        join public.graphs as graph
          on graph.id = node.graph_id
        where node.id = target_id
    """,
    "atom": """
        select item.project_id
        from public.atoms as item
        where item.id = target_id
    """,
    "task": """
        select item.project_id
        from public.research_tasks as item
        where item.id = target_id
    """,
    "claim": """
        select item.project_id
        from public.research_claims as item
        where item.id = target_id
    """,
    "evidence": """
        select item.project_id
        from public.research_evidence as item
        where item.id = target_id
    """,
    "plan": """
        select item.project_id
        from public.plan_versions as item
        where item.id = target_id
    """,
    "export": """
        select item.project_id
        from public.exports as item
        where item.id = target_id
    """,
    "execution_run": """
        select item.project_id
        from public.execution_runs as item
        where item.id = target_id
    """,
    "execution_node": """
        select run.project_id
        from public.execution_run_nodes as node
        join public.execution_runs as run
          on run.id = node.run_id
        where node.id = target_id
    """,
    "execution_attempt": """
        select run.project_id
        from public.execution_attempts as attempt
        join public.execution_run_nodes as node
          on node.id = attempt.run_node_id
        join public.execution_runs as run
          on run.id = node.run_id
        where attempt.id = target_id
    """,
    "execution_receipt": """
        select run.project_id
        from public.execution_receipts as receipt
        join public.execution_runs as run
          on run.id = receipt.run_id
        where receipt.id = target_id
    """,
    "provider": """
        select item.project_id
        from public.provider_configs as item
        where item.id = target_id
    """,
    "paid_unlock": """
        select item.project_id
        from public.paid_route_unlocks as item
        where item.id = target_id
    """,
}


def normalize_sql(value: str) -> str:
    return dedent(value).strip()


def build_migration() -> str:
    parts = [
        dedent(
            """
            alter table public.projects
                add column if not exists owner_id uuid;

            alter table public.projects
                alter column owner_id
                set default auth.uid();

            do $$
            begin
                if not exists (
                    select 1
                    from pg_constraint
                    where conname = 'projects_owner_id_fkey'
                      and conrelid = 'public.projects'::regclass
                ) then
                    alter table public.projects
                        add constraint projects_owner_id_fkey
                        foreign key(owner_id)
                        references auth.users(id)
                        on delete cascade;
                end if;
            end;
            $$;

            do $$
            begin
                if exists (
                    select 1
                    from public.projects
                    where owner_id is null
                ) then
                    raise exception
                        'Cannot enable ownership: projects with null owner_id exist';
                end if;
            end;
            $$;

            alter table public.projects
                alter column owner_id
                set not null;

            create index if not exists idx_projects_owner
                on public.projects(owner_id);

            create or replace function
                public.specgraph_is_project_owner(
                    target_project_id uuid
                )
            returns boolean
            language sql
            stable
            security definer
            set search_path = ''
            as $$
                select
                    (select auth.uid()) is not null
                    and exists (
                        select 1
                        from public.projects as project
                        where project.id = target_project_id
                          and project.owner_id =
                              (select auth.uid())
                    )
            $$;

            revoke all on function
                public.specgraph_is_project_owner(uuid)
                from public;

            grant execute on function
                public.specgraph_is_project_owner(uuid)
                to authenticated, service_role;
            """
        ).strip()
    ]

    for name, body in PROJECT_ID_FUNCTIONS.items():
        function_name = (
            f"public.specgraph_{name}_project_id"
        )

        parts.append(
            dedent(
                f"""
                create or replace function
                    {function_name}(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    {normalize_sql(body)}
                $$;

                revoke all on function
                    {function_name}(uuid)
                    from public;

                grant execute on function
                    {function_name}(uuid)
                    to authenticated, service_role;
                """
            ).strip()
        )

    parts.append(
        dedent(
            """
            revoke all privileges
                on all tables in schema public
                from anon;

            revoke all privileges
                on all sequences in schema public
                from anon;

            revoke all privileges
                on all tables in schema public
                from public;

            grant usage on schema public
                to authenticated, service_role;

            grant select, insert, update, delete
                on all tables in schema public
                to authenticated;

            grant usage, select
                on all sequences in schema public
                to authenticated;

            grant all privileges
                on all tables in schema public
                to service_role;

            grant all privileges
                on all sequences in schema public
                to service_role;

            alter default privileges
                in schema public
                revoke all on tables from anon;

            alter default privileges
                in schema public
                grant select, insert, update, delete
                on tables to authenticated;

            alter default privileges
                in schema public
                grant all on tables to service_role;

            drop policy if exists
                "project_owner_all"
                on public.projects;

            create policy
                "project_owner_all"
                on public.projects
                for all
                to authenticated
                using (
                    (select auth.uid()) is not null
                    and owner_id = (select auth.uid())
                )
                with check (
                    (select auth.uid()) is not null
                    and owner_id = (select auth.uid())
                );
            """
        ).strip()
    )

    policies = {
        **DIRECT_POLICIES,
        **INDIRECT_POLICIES,
    }

    for table in sorted(policies):
        condition = normalize_sql(
            policies[table]
        )

        parts.append(
            dedent(
                f"""
                drop policy if exists
                    "project_owner_all"
                    on public.{table};

                create policy
                    "project_owner_all"
                    on public.{table}
                    for all
                    to authenticated
                    using (
                        {condition}
                    )
                    with check (
                        {condition}
                    );
                """
            ).strip()
        )

    parts.append(
        dedent(
            """
            comment on column public.projects.owner_id is
                'Supabase Auth user who owns this project.';

            comment on function
                public.specgraph_is_project_owner(uuid)
            is
                'RLS helper that resolves project ownership without recursive policies.';
            """
        ).strip()
    )

    return "\n\n".join(parts) + "\n"


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    target.write_text(
        content,
        encoding="utf-8",
    )
    print(f"WROTE {path}")


migration = build_migration()

write(
    "infra/supabase/migrations/"
    "202607120008_auth_rls.sql",
    migration,
)

write(
    "supabase/migrations/"
    "20260712000900_auth_rls.sql",
    migration,
)

expected_tables = sorted(
    {
        "projects",
        *DIRECT_POLICIES,
        *INDIRECT_POLICIES,
    }
)

test_content = f'''
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

DEPLOYMENT = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712000900_auth_rls.sql"
)

SOURCE = (
    ROOT
    / "infra"
    / "supabase"
    / "migrations"
    / "202607120008_auth_rls.sql"
)

EXPECTED_TABLES = {expected_tables!r}


class SupabaseRlsMigrationTest(
    unittest.TestCase
):
    def setUp(self) -> None:
        self.sql = DEPLOYMENT.read_text(
            encoding="utf-8"
        )

    def test_source_and_deployment_match(
        self,
    ) -> None:
        self.assertEqual(
            SOURCE.read_bytes(),
            DEPLOYMENT.read_bytes(),
        )

    def test_every_public_table_has_policy(
        self,
    ) -> None:
        found = set(
            re.findall(
                r'on public\\.([a-z_]+)\\s+'
                r'for all\\s+'
                r'to authenticated',
                self.sql,
                flags=re.IGNORECASE,
            )
        )

        self.assertEqual(
            found,
            set(EXPECTED_TABLES),
        )

    def test_projects_require_owner(
        self,
    ) -> None:
        self.assertIn(
            "alter column owner_id",
            self.sql,
        )
        self.assertIn(
            "set not null",
            self.sql,
        )
        self.assertIn(
            "references auth.users(id)",
            self.sql,
        )
        self.assertIn(
            "owner_id = (select auth.uid())",
            self.sql,
        )

    def test_anonymous_role_has_no_access(
        self,
    ) -> None:
        self.assertIn(
            "from anon",
            self.sql,
        )
        self.assertNotRegex(
            self.sql,
            r'create policy[\\s\\S]*?to anon',
        )

    def test_security_definer_functions_lock_search_path(
        self,
    ) -> None:
        function_count = len(
            re.findall(
                r"security definer",
                self.sql,
                flags=re.IGNORECASE,
            )
        )

        search_path_count = len(
            re.findall(
                r"set search_path = ''",
                self.sql,
                flags=re.IGNORECASE,
            )
        )

        self.assertEqual(
            function_count,
            search_path_count,
        )
        self.assertGreater(
            function_count,
            10,
        )


if __name__ == "__main__":
    unittest.main()
'''

write(
    "tests/test_supabase_rls.py",
    dedent(test_content).lstrip(),
)

readme_path = ROOT / "README.md"
readme = readme_path.read_text(
    encoding="utf-8"
)

section = dedent(
    """

    ## Supabase authentication and project ownership

    Hosted projects are owned by a Supabase Auth user through
    `projects.owner_id`.

    Every public table has an authenticated project-owner RLS
    policy. Child records resolve ownership through their
    authoritative parent project, and policies also reject
    cross-project foreign-key combinations.

    Anonymous API clients receive no table privileges. The
    `service_role` remains available for trusted backend
    administration and runtime synchronization.
    """
)

if (
    "## Supabase authentication and project ownership"
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
    f"AUTH/RLS MIGRATION CREATED FOR "
    f"{len(expected_tables)} TABLES"
)
