create table if not exists public.integration_bindings (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    system_name text not null,
    binding_type text not null,
    config_json jsonb not null
        default '{}'::jsonb,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(
        project_id,
        system_name,
        binding_type
    )
);

create table if not exists public.exports (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    plan_version_id uuid not null
        references public.plan_versions(id)
        on delete cascade,
    export_type text not null,
    bundle_fingerprint text not null,
    output_path text not null,
    manifest_sha256 text not null,
    status text not null,
    artifact_count bigint not null,
    created_at timestamptz not null default now(),
    verified_at timestamptz,
    unique(
        plan_version_id,
        export_type,
        bundle_fingerprint
    )
);

create table if not exists public.export_verification_findings (
    id uuid primary key default gen_random_uuid(),
    export_id uuid not null
        references public.exports(id)
        on delete cascade,
    severity text not null,
    code text not null,
    message text not null,
    artifact_path text,
    created_at timestamptz not null default now()
);

create index if not exists idx_integration_bindings_project
    on public.integration_bindings(
        project_id,
        enabled
    );

create index if not exists idx_exports_project
    on public.exports(
        project_id,
        created_at
    );

create index if not exists idx_exports_plan
    on public.exports(
        plan_version_id,
        export_type
    );

alter table public.integration_bindings
    enable row level security;

alter table public.exports
    enable row level security;

alter table public.export_verification_findings
    enable row level security;
