create table if not exists public.authority_relations (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    from_atom_id uuid not null
        references public.atoms(id)
        on delete cascade,
    to_atom_id uuid not null
        references public.atoms(id)
        on delete cascade,
    relation_type text not null,
    rationale text not null default '',
    confidence double precision not null,
    inferred boolean not null default false,
    created_at timestamptz not null default now(),
    check(from_atom_id <> to_atom_id),
    check(confidence >= 0.0 and confidence <= 1.0),
    unique(
        project_id,
        from_atom_id,
        to_atom_id,
        relation_type
    )
);

create table if not exists public.plan_versions (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    authority_graph_id uuid not null
        references public.graphs(id)
        on delete cascade,
    execution_graph_id uuid not null
        references public.graphs(id)
        on delete cascade,
    input_fingerprint text not null,
    status text not null,
    allow_open_research boolean not null default false,
    atom_count bigint not null,
    node_count bigint not null,
    edge_count bigint not null,
    open_dimension_count bigint not null,
    created_at timestamptz not null default now(),
    verified_at timestamptz,
    unique(
        project_id,
        input_fingerprint,
        allow_open_research
    )
);

create table if not exists public.plan_node_bindings (
    id uuid primary key default gen_random_uuid(),
    plan_version_id uuid not null
        references public.plan_versions(id)
        on delete cascade,
    graph_node_id uuid not null
        references public.graph_nodes(id)
        on delete cascade,
    atom_id uuid not null
        references public.atoms(id)
        on delete cascade,
    stage text not null,
    sequence_number bigint not null,
    created_at timestamptz not null default now(),
    unique(
        plan_version_id,
        atom_id,
        stage
    ),
    unique(
        plan_version_id,
        graph_node_id
    )
);

create table if not exists public.plan_verification_findings (
    id uuid primary key default gen_random_uuid(),
    plan_version_id uuid not null
        references public.plan_versions(id)
        on delete cascade,
    severity text not null,
    code text not null,
    message text not null,
    entity_id text,
    created_at timestamptz not null default now()
);

create index if not exists idx_authority_relations_project
    on public.authority_relations(
        project_id,
        relation_type
    );

create index if not exists idx_plan_versions_project
    on public.plan_versions(
        project_id,
        created_at
    );

create index if not exists idx_plan_bindings_plan
    on public.plan_node_bindings(
        plan_version_id,
        sequence_number
    );

alter table public.authority_relations
    enable row level security;

alter table public.plan_versions
    enable row level security;

alter table public.plan_node_bindings
    enable row level security;

alter table public.plan_verification_findings
    enable row level security;
