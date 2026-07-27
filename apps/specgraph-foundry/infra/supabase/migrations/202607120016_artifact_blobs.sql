create table if not exists public.artifact_blobs (
    id uuid primary key default gen_random_uuid(),
    object_path text not null unique,
    media_type text not null,
    data bytea not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_artifact_blobs_path
    on public.artifact_blobs(object_path);

alter table public.artifact_blobs
    enable row level security;
