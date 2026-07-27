create table if not exists public.research_evidence (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    task_id uuid not null
        references public.research_tasks(id)
        on delete cascade,
    atom_id uuid not null
        references public.atoms(id)
        on delete cascade,
    dimension text not null,
    source_uri text not null,
    source_title text not null,
    publisher text not null default '',
    evidence_type text not null,
    excerpt text not null,
    content_sha256 text not null,
    reliability double precision not null,
    retrieved_at timestamptz not null,
    created_at timestamptz not null default now(),
    check(reliability >= 0.0 and reliability <= 1.0),
    unique(task_id, source_uri, content_sha256)
);

create table if not exists public.research_claims (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    task_id uuid not null unique
        references public.research_tasks(id)
        on delete cascade,
    atom_id uuid not null
        references public.atoms(id)
        on delete cascade,
    dimension text not null,
    conclusion text not null,
    applicability text not null,
    confidence double precision not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.research_claim_evidence (
    claim_id uuid not null
        references public.research_claims(id)
        on delete cascade,
    evidence_id uuid not null
        references public.research_evidence(id)
        on delete cascade,
    primary key(claim_id, evidence_id)
);

create table if not exists public.research_task_events (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null
        references public.research_tasks(id)
        on delete cascade,
    event_type text not null,
    worker_id text,
    payload_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

alter table public.research_evidence
    enable row level security;

alter table public.research_claims
    enable row level security;

alter table public.research_claim_evidence
    enable row level security;

alter table public.research_task_events
    enable row level security;
