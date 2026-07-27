alter table public.source_documents
    add column if not exists
    media_type text not null
    default 'text/plain';

create table if not exists public.ingestion_runs (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    document_id uuid
        references public.source_documents(id)
        on delete cascade,
    status text not null,
    chunk_bytes bigint not null,
    section_count bigint not null default 0,
    chunk_count bigint not null default 0,
    covered_bytes bigint not null default 0,
    coverage_sha256 text,
    error_message text,
    created_at timestamptz not null default now(),
    completed_at timestamptz
);

create table if not exists public.source_sections (
    id uuid primary key default gen_random_uuid(),
    document_id uuid not null
        references public.source_documents(id)
        on delete cascade,
    ordinal bigint not null,
    title text not null,
    heading_level integer,
    byte_start bigint not null,
    byte_end bigint not null,
    line_start bigint not null,
    line_end bigint not null,
    created_at timestamptz not null default now(),
    unique(document_id, ordinal)
);

create table if not exists public.source_chunks (
    id uuid primary key default gen_random_uuid(),
    document_id uuid not null
        references public.source_documents(id)
        on delete cascade,
    section_id uuid
        references public.source_sections(id)
        on delete cascade,
    ordinal bigint not null,
    sha256 text not null,
    byte_start bigint not null,
    byte_end bigint not null,
    line_start bigint not null,
    line_end bigint not null,
    content text not null,
    created_at timestamptz not null default now(),
    unique(document_id, ordinal)
);

alter table public.ingestion_runs
    enable row level security;

alter table public.source_sections
    enable row level security;

alter table public.source_chunks
    enable row level security;
