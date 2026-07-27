create table if not exists public.extraction_runs (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    document_id uuid not null
        references public.source_documents(id)
        on delete cascade,
    extractor_version text not null,
    source_sha256 text not null,
    status text not null,
    scanned_bytes bigint not null default 0,
    scanned_lines bigint not null default 0,
    statement_count bigint not null default 0,
    atom_count bigint not null default 0,
    dimension_count bigint not null default 0,
    research_task_count bigint not null default 0,
    error_message text,
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    unique(
        document_id,
        extractor_version,
        source_sha256
    )
);

create table if not exists public.atoms (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    document_id uuid not null
        references public.source_documents(id)
        on delete cascade,
    section_id uuid
        references public.source_sections(id)
        on delete set null,
    extraction_run_id uuid not null
        references public.extraction_runs(id)
        on delete cascade,
    ordinal bigint not null,
    kind text not null,
    modality text not null,
    status text not null,
    canonical_statement text not null,
    exact_quote text not null,
    byte_start bigint not null,
    byte_end bigint not null,
    line_start bigint not null,
    line_end bigint not null,
    source_sha256 text not null,
    confidence double precision not null,
    created_at timestamptz not null default now(),
    check(byte_start >= 0),
    check(byte_end > byte_start),
    check(line_start > 0),
    check(line_end >= line_start),
    check(confidence >= 0.0 and confidence <= 1.0),
    unique(
        document_id,
        byte_start,
        byte_end,
        canonical_statement
    )
);

create table if not exists public.atom_dimensions (
    id uuid primary key default gen_random_uuid(),
    atom_id uuid not null
        references public.atoms(id)
        on delete cascade,
    dimension text not null,
    applicability text not null,
    status text not null,
    rationale text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(atom_id, dimension)
);

create table if not exists public.research_tasks (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    atom_id uuid not null
        references public.atoms(id)
        on delete cascade,
    dimension text not null,
    question text not null,
    status text not null,
    priority integer not null default 100,
    attempt_count integer not null default 0,
    lease_owner text,
    lease_expires_at timestamptz,
    result_json jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(atom_id, dimension)
);

create index if not exists idx_atoms_document
    on public.atoms(document_id, ordinal);

create index if not exists idx_atoms_project
    on public.atoms(project_id, kind, modality);

create index if not exists idx_research_tasks_project
    on public.research_tasks(
        project_id,
        status,
        priority
    );

alter table public.extraction_runs
    enable row level security;

alter table public.atoms
    enable row level security;

alter table public.atom_dimensions
    enable row level security;

alter table public.research_tasks
    enable row level security;
