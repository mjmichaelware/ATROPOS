insert into storage.buckets(
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values(
    'source-documents',
    'source-documents',
    false,
    10485760,
    array[
        'text/plain',
        'text/markdown',
        'text/yaml',
        'application/json',
        'application/yaml',
        'application/x-yaml'
    ]::text[]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create table if not exists public.source_uploads (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null
        references auth.users(id)
        on delete cascade,
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    bucket text not null,
    object_path text not null unique,
    original_filename text not null,
    declared_media_type text not null,
    expected_bytes bigint not null
        check(expected_bytes > 0),
    expected_sha256 text not null
        check(expected_sha256 ~ '^[0-9a-f]{64}$'),
    status text not null
        check(
            status in (
                'PENDING',
                'UPLOADED',
                'FINALIZING',
                'FINALIZED',
                'FAILED',
                'EXPIRED'
            )
        ),
    actual_bytes bigint
        check(
            actual_bytes is null
            or actual_bytes >= 0
        ),
    actual_sha256 text
        check(
            actual_sha256 is null
            or actual_sha256 ~ '^[0-9a-f]{64}$'
        ),
    document_id uuid unique
        references public.source_documents(id)
        on delete set null,
    failure_code text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null,
    finalized_at timestamptz
);

create index if not exists idx_source_uploads_owner
    on public.source_uploads(
        owner_id,
        created_at,
        id
    );

create index if not exists idx_source_uploads_project
    on public.source_uploads(
        project_id,
        created_at,
        id
    );

alter table public.source_documents
    add column if not exists source_upload_id uuid
    references public.source_uploads(id)
    on delete set null;

alter table public.source_uploads
    enable row level security;

drop policy if exists
    "project_owner_all"
    on public.source_uploads;

create policy
    "project_owner_all"
    on public.source_uploads
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
    "specgraph_source_upload_insert"
    on storage.objects;

create policy
    "specgraph_source_upload_insert"
    on storage.objects
    for insert
    to authenticated
    with check (
        bucket_id = 'source-documents'
        and split_part(name, '/', 1) = ((select auth.uid())::text)
        and array_length(string_to_array(name, '/'), 1) = 4
        and split_part(name, '/', 4) = 'source'
    );

drop policy if exists
    "specgraph_source_upload_select"
    on storage.objects;

create policy
    "specgraph_source_upload_select"
    on storage.objects
    for select
    to authenticated
    using (
        bucket_id = 'source-documents'
        and split_part(name, '/', 1) = ((select auth.uid())::text)
        and array_length(string_to_array(name, '/'), 1) = 4
        and split_part(name, '/', 4) = 'source'
    );
