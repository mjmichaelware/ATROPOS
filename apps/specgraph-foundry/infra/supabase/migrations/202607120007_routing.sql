create table if not exists public.project_policies (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null unique
        references public.projects(id)
        on delete cascade,
    route_law_json jsonb not null,
    allow_offline_degraded boolean not null
        default true,
    paid_emergency_enabled boolean not null
        default false,
    max_paid_decisions_per_unlock bigint not null
        default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check(max_paid_decisions_per_unlock > 0)
);

create table if not exists public.provider_configs (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    name text not null,
    provider_class text not null,
    cost_class text not null,
    territories_json jsonb not null,
    priority bigint not null,
    enabled boolean not null default true,
    status text not null default 'UNKNOWN',
    cooldown_until timestamptz,
    metadata_json jsonb not null
        default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(project_id, name)
);

create table if not exists public.renderer_configs (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    name text not null,
    renderer_type text not null,
    territories_json jsonb not null,
    priority bigint not null,
    enabled boolean not null default true,
    status text not null default 'READY',
    metadata_json jsonb not null
        default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(project_id, name)
);

create table if not exists public.provider_health_events (
    id uuid primary key default gen_random_uuid(),
    provider_id uuid not null
        references public.provider_configs(id)
        on delete cascade,
    status text not null,
    latency_ms double precision,
    error_message text,
    cooldown_until timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists public.paid_route_unlocks (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    provider_id uuid
        references public.provider_configs(id)
        on delete cascade,
    actor_id text not null,
    reason text not null,
    max_decisions bigint not null,
    used_count bigint not null default 0,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    check(max_decisions > 0),
    check(used_count >= 0),
    check(used_count <= max_decisions)
);

create table if not exists public.route_decisions (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    territory text not null,
    decision_type text not null,
    selected_provider_id uuid
        references public.provider_configs(id)
        on delete set null,
    paid_unlock_id uuid
        references public.paid_route_unlocks(id)
        on delete set null,
    retry_at timestamptz,
    rationale text not null,
    input_json jsonb not null,
    considered_json jsonb not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_provider_configs_project
    on public.provider_configs(
        project_id,
        provider_class,
        enabled,
        priority
    );

create index if not exists idx_renderer_configs_project
    on public.renderer_configs(
        project_id,
        enabled,
        priority
    );

create index if not exists idx_paid_unlocks_project
    on public.paid_route_unlocks(
        project_id,
        expires_at
    );

create index if not exists idx_route_decisions_project
    on public.route_decisions(
        project_id,
        created_at
    );

alter table public.project_policies
    enable row level security;

alter table public.provider_configs
    enable row level security;

alter table public.renderer_configs
    enable row level security;

alter table public.provider_health_events
    enable row level security;

alter table public.paid_route_unlocks
    enable row level security;

alter table public.route_decisions
    enable row level security;
