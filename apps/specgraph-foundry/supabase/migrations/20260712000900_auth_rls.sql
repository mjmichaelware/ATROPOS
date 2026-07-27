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

create or replace function
                    public.specgraph_document_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.source_documents as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_document_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_document_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_section_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select document.project_id
from public.source_sections as section
join public.source_documents as document
  on document.id = section.document_id
where section.id = target_id
                $$;

                revoke all on function
                    public.specgraph_section_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_section_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_graph_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.graphs as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_graph_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_graph_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_graph_node_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select graph.project_id
from public.graph_nodes as node
join public.graphs as graph
  on graph.id = node.graph_id
where node.id = target_id
                $$;

                revoke all on function
                    public.specgraph_graph_node_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_graph_node_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_atom_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.atoms as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_atom_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_atom_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_task_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.research_tasks as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_task_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_task_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_claim_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.research_claims as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_claim_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_claim_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_evidence_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.research_evidence as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_evidence_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_evidence_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_plan_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.plan_versions as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_plan_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_plan_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_export_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.exports as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_export_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_export_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_execution_run_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.execution_runs as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_execution_run_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_execution_run_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_execution_node_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select run.project_id
from public.execution_run_nodes as node
join public.execution_runs as run
  on run.id = node.run_id
where node.id = target_id
                $$;

                revoke all on function
                    public.specgraph_execution_node_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_execution_node_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_execution_attempt_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select run.project_id
from public.execution_attempts as attempt
join public.execution_run_nodes as node
  on node.id = attempt.run_node_id
join public.execution_runs as run
  on run.id = node.run_id
where attempt.id = target_id
                $$;

                revoke all on function
                    public.specgraph_execution_attempt_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_execution_attempt_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_execution_receipt_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select run.project_id
from public.execution_receipts as receipt
join public.execution_runs as run
  on run.id = receipt.run_id
where receipt.id = target_id
                $$;

                revoke all on function
                    public.specgraph_execution_receipt_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_execution_receipt_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_provider_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.provider_configs as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_provider_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_provider_project_id(uuid)
                    to authenticated, service_role;

create or replace function
                    public.specgraph_paid_unlock_project_id(
                        target_id uuid
                    )
                returns uuid
                language sql
                stable
                security definer
                set search_path = ''
                as $$
                    select item.project_id
from public.paid_route_unlocks as item
where item.id = target_id
                $$;

                revoke all on function
                    public.specgraph_paid_unlock_project_id(uuid)
                    from public;

                grant execute on function
                    public.specgraph_paid_unlock_project_id(uuid)
                    to authenticated, service_role;

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

drop policy if exists
                    "project_owner_all"
                    on public.atom_dimensions;

                create policy
                    "project_owner_all"
                    on public.atom_dimensions
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(
    public.specgraph_atom_project_id(
        atom_id
    )
)
                    )
                    with check (
                        public.specgraph_is_project_owner(
    public.specgraph_atom_project_id(
        atom_id
    )
)
                    );

drop policy if exists
                    "project_owner_all"
                    on public.atoms;

                create policy
                    "project_owner_all"
                    on public.atoms
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.authority_relations;

                create policy
                    "project_owner_all"
                    on public.authority_relations
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_atom_project_id(
    from_atom_id
) = project_id
and public.specgraph_atom_project_id(
    to_atom_id
) = project_id
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_atom_project_id(
    from_atom_id
) = project_id
and public.specgraph_atom_project_id(
    to_atom_id
) = project_id
                    );

drop policy if exists
                    "project_owner_all"
                    on public.execution_attempts;

                create policy
                    "project_owner_all"
                    on public.execution_attempts
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(
    public.specgraph_execution_node_project_id(
        run_node_id
    )
)
                    )
                    with check (
                        public.specgraph_is_project_owner(
    public.specgraph_execution_node_project_id(
        run_node_id
    )
)
                    );

drop policy if exists
                    "project_owner_all"
                    on public.execution_events;

                create policy
                    "project_owner_all"
                    on public.execution_events
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.execution_receipts;

                create policy
                    "project_owner_all"
                    on public.execution_receipts
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.execution_run_nodes;

                create policy
                    "project_owner_all"
                    on public.execution_run_nodes
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.execution_runs;

                create policy
                    "project_owner_all"
                    on public.execution_runs
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.execution_validation_findings;

                create policy
                    "project_owner_all"
                    on public.execution_validation_findings
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.export_verification_findings;

                create policy
                    "project_owner_all"
                    on public.export_verification_findings
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(
    public.specgraph_export_project_id(
        export_id
    )
)
                    )
                    with check (
                        public.specgraph_is_project_owner(
    public.specgraph_export_project_id(
        export_id
    )
)
                    );

drop policy if exists
                    "project_owner_all"
                    on public.exports;

                create policy
                    "project_owner_all"
                    on public.exports
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_plan_project_id(
    plan_version_id
) = project_id
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_plan_project_id(
    plan_version_id
) = project_id
                    );

drop policy if exists
                    "project_owner_all"
                    on public.extraction_runs;

                create policy
                    "project_owner_all"
                    on public.extraction_runs
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_document_project_id(
    document_id
) = project_id
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_document_project_id(
    document_id
) = project_id
                    );

drop policy if exists
                    "project_owner_all"
                    on public.graph_edges;

                create policy
                    "project_owner_all"
                    on public.graph_edges
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.graph_nodes;

                create policy
                    "project_owner_all"
                    on public.graph_nodes
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(
    public.specgraph_graph_project_id(
        graph_id
    )
)
                    )
                    with check (
                        public.specgraph_is_project_owner(
    public.specgraph_graph_project_id(
        graph_id
    )
)
                    );

drop policy if exists
    "project_owner_all"
    on public.graphs;

create policy
    "project_owner_all"
    on public.graphs
    for all
    to authenticated
    using (
        public.specgraph_is_project_owner(project_id)
    )
    with check (
        public.specgraph_is_project_owner(project_id)
    );

drop policy if exists
                    "project_owner_all"
                    on public.ingestion_runs;

                create policy
                    "project_owner_all"
                    on public.ingestion_runs
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and (
    document_id is null
    or public.specgraph_document_project_id(
        document_id
    ) = project_id
)
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and (
    document_id is null
    or public.specgraph_document_project_id(
        document_id
    ) = project_id
)
                    );

drop policy if exists
    "project_owner_all"
    on public.integration_bindings;

create policy
    "project_owner_all"
    on public.integration_bindings
    for all
    to authenticated
    using (
        public.specgraph_is_project_owner(project_id)
    )
    with check (
        public.specgraph_is_project_owner(project_id)
    );

drop policy if exists
                    "project_owner_all"
                    on public.paid_route_unlocks;

                create policy
                    "project_owner_all"
                    on public.paid_route_unlocks
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and (
    provider_id is null
    or public.specgraph_provider_project_id(
        provider_id
    ) = project_id
)
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and (
    provider_id is null
    or public.specgraph_provider_project_id(
        provider_id
    ) = project_id
)
                    );

drop policy if exists
                    "project_owner_all"
                    on public.plan_node_bindings;

                create policy
                    "project_owner_all"
                    on public.plan_node_bindings
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.plan_verification_findings;

                create policy
                    "project_owner_all"
                    on public.plan_verification_findings
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(
    public.specgraph_plan_project_id(
        plan_version_id
    )
)
                    )
                    with check (
                        public.specgraph_is_project_owner(
    public.specgraph_plan_project_id(
        plan_version_id
    )
)
                    );

drop policy if exists
                    "project_owner_all"
                    on public.plan_versions;

                create policy
                    "project_owner_all"
                    on public.plan_versions
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_graph_project_id(
    authority_graph_id
) = project_id
and public.specgraph_graph_project_id(
    execution_graph_id
) = project_id
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_graph_project_id(
    authority_graph_id
) = project_id
and public.specgraph_graph_project_id(
    execution_graph_id
) = project_id
                    );

drop policy if exists
    "project_owner_all"
    on public.project_policies;

create policy
    "project_owner_all"
    on public.project_policies
    for all
    to authenticated
    using (
        public.specgraph_is_project_owner(project_id)
    )
    with check (
        public.specgraph_is_project_owner(project_id)
    );

drop policy if exists
    "project_owner_all"
    on public.provider_configs;

create policy
    "project_owner_all"
    on public.provider_configs
    for all
    to authenticated
    using (
        public.specgraph_is_project_owner(project_id)
    )
    with check (
        public.specgraph_is_project_owner(project_id)
    );

drop policy if exists
                    "project_owner_all"
                    on public.provider_health_events;

                create policy
                    "project_owner_all"
                    on public.provider_health_events
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(
    public.specgraph_provider_project_id(
        provider_id
    )
)
                    )
                    with check (
                        public.specgraph_is_project_owner(
    public.specgraph_provider_project_id(
        provider_id
    )
)
                    );

drop policy if exists
    "project_owner_all"
    on public.renderer_configs;

create policy
    "project_owner_all"
    on public.renderer_configs
    for all
    to authenticated
    using (
        public.specgraph_is_project_owner(project_id)
    )
    with check (
        public.specgraph_is_project_owner(project_id)
    );

drop policy if exists
                    "project_owner_all"
                    on public.research_claim_evidence;

                create policy
                    "project_owner_all"
                    on public.research_claim_evidence
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.research_claims;

                create policy
                    "project_owner_all"
                    on public.research_claims
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_task_project_id(
    task_id
) = project_id
and public.specgraph_atom_project_id(
    atom_id
) = project_id
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_task_project_id(
    task_id
) = project_id
and public.specgraph_atom_project_id(
    atom_id
) = project_id
                    );

drop policy if exists
                    "project_owner_all"
                    on public.research_evidence;

                create policy
                    "project_owner_all"
                    on public.research_evidence
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_task_project_id(
    task_id
) = project_id
and public.specgraph_atom_project_id(
    atom_id
) = project_id
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_task_project_id(
    task_id
) = project_id
and public.specgraph_atom_project_id(
    atom_id
) = project_id
                    );

drop policy if exists
                    "project_owner_all"
                    on public.research_task_events;

                create policy
                    "project_owner_all"
                    on public.research_task_events
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(
    public.specgraph_task_project_id(
        task_id
    )
)
                    )
                    with check (
                        public.specgraph_is_project_owner(
    public.specgraph_task_project_id(
        task_id
    )
)
                    );

drop policy if exists
                    "project_owner_all"
                    on public.research_tasks;

                create policy
                    "project_owner_all"
                    on public.research_tasks
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_atom_project_id(
    atom_id
) = project_id
                    )
                    with check (
                        public.specgraph_is_project_owner(project_id)
and public.specgraph_atom_project_id(
    atom_id
) = project_id
                    );

drop policy if exists
                    "project_owner_all"
                    on public.route_decisions;

                create policy
                    "project_owner_all"
                    on public.route_decisions
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
                    "project_owner_all"
                    on public.source_chunks;

                create policy
                    "project_owner_all"
                    on public.source_chunks
                    for all
                    to authenticated
                    using (
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
                    )
                    with check (
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
                    );

drop policy if exists
    "project_owner_all"
    on public.source_documents;

create policy
    "project_owner_all"
    on public.source_documents
    for all
    to authenticated
    using (
        public.specgraph_is_project_owner(project_id)
    )
    with check (
        public.specgraph_is_project_owner(project_id)
    );

drop policy if exists
                    "project_owner_all"
                    on public.source_sections;

                create policy
                    "project_owner_all"
                    on public.source_sections
                    for all
                    to authenticated
                    using (
                        public.specgraph_is_project_owner(
    public.specgraph_document_project_id(
        document_id
    )
)
                    )
                    with check (
                        public.specgraph_is_project_owner(
    public.specgraph_document_project_id(
        document_id
    )
)
                    );

comment on column public.projects.owner_id is
    'Supabase Auth user who owns this project.';

comment on function
    public.specgraph_is_project_owner(uuid)
is
    'RLS helper that resolves project ownership without recursive policies.';
