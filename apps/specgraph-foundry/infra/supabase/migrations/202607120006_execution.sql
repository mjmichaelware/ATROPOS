create table if not exists public.execution_runs (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    plan_version_id uuid not null
        references public.plan_versions(id)
        on delete cascade,
    export_id uuid
        references public.exports(id)
        on delete set null,
    runtime_system text not null,
    runtime_run_id text not null,
    status text not null,
    input_fingerprint text not null,
    created_at timestamptz not null default now(),
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    verified_at timestamptz,
    unique(
        runtime_system,
        runtime_run_id
    )
);

create table if not exists public.execution_run_nodes (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null
        references public.execution_runs(id)
        on delete cascade,
    graph_node_id uuid not null
        references public.graph_nodes(id)
        on delete cascade,
    atom_id uuid not null
        references public.atoms(id)
        on delete cascade,
    stage text not null,
    sequence_number bigint not null,
    title text not null,
    status text not null,
    lease_owner text,
    lease_expires_at timestamptz,
    attempt_count bigint not null default 0,
    accepted_receipt_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(
        run_id,
        graph_node_id
    )
);

create table if not exists public.execution_attempts (
    id uuid primary key default gen_random_uuid(),
    run_node_id uuid not null
        references public.execution_run_nodes(id)
        on delete cascade,
    worker_id text not null,
    status text not null,
    lease_expires_at timestamptz not null,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    error_message text
);

create table if not exists public.execution_receipts (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null
        references public.execution_runs(id)
        on delete cascade,
    run_node_id uuid not null
        references public.execution_run_nodes(id)
        on delete cascade,
    attempt_id uuid not null
        references public.execution_attempts(id)
        on delete cascade,
    actor_system text not null,
    actor_id text not null,
    outcome text not null,
    summary text not null,
    evidence_json jsonb not null,
    evidence_sha256 text not null,
    validation_status text not null,
    created_at timestamptz not null default now(),
    unique(
        run_node_id,
        evidence_sha256
    )
);

create table if not exists public.execution_validation_findings (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null
        references public.execution_runs(id)
        on delete cascade,
    run_node_id uuid
        references public.execution_run_nodes(id)
        on delete cascade,
    receipt_id uuid
        references public.execution_receipts(id)
        on delete cascade,
    gate_code text not null,
    severity text not null,
    message text not null,
    created_at timestamptz not null default now()
);

create table if not exists public.execution_events (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null
        references public.execution_runs(id)
        on delete cascade,
    run_node_id uuid
        references public.execution_run_nodes(id)
        on delete cascade,
    event_type text not null,
    actor_id text,
    payload_json jsonb not null
        default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_execution_runs_project
    on public.execution_runs(
        project_id,
        created_at
    );

create index if not exists idx_execution_nodes_run
    on public.execution_run_nodes(
        run_id,
        status,
        sequence_number
    );

create index if not exists idx_execution_receipts_node
    on public.execution_receipts(
        run_node_id,
        validation_status
    );

alter table public.execution_runs
    enable row level security;

alter table public.execution_run_nodes
    enable row level security;

alter table public.execution_attempts
    enable row level security;

alter table public.execution_receipts
    enable row level security;

alter table public.execution_validation_findings
    enable row level security;

alter table public.execution_events
    enable row level security;
