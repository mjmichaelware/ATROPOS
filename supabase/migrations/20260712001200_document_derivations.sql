alter table public.source_uploads
    drop constraint if exists source_uploads_document_id_key;

alter table public.source_documents
    drop constraint if exists source_documents_project_id_sha256_key;

update storage.buckets
set allowed_mime_types = array[
    'text/plain',
    'text/markdown',
    'text/yaml',
    'application/json',
    'application/yaml',
    'application/x-yaml',
    'text/html',
    'application/xhtml+xml',
    'application/pdf',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'text/x-python',
    'text/x-java-source',
    'text/x-c',
    'text/x-c++src',
    'text/x-go',
    'text/x-rustsrc'
]::text[]
where id = 'source-documents';

create table if not exists public.document_derivations (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null
        references auth.users(id)
        on delete cascade,
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    source_upload_id uuid not null unique
        references public.source_uploads(id)
        on delete cascade,
    source_document_id uuid not null unique
        references public.source_documents(id)
        on delete cascade,
    adapter_name text not null,
    adapter_version text not null,
    original_media_type text not null,
    detected_media_type text not null,
    original_byte_count bigint not null
        check(original_byte_count > 0),
    original_sha256 text not null
        check(original_sha256 ~ '^[0-9a-f]{64}$'),
    derived_byte_count bigint not null
        check(derived_byte_count > 0),
    derived_sha256 text not null
        check(derived_sha256 ~ '^[0-9a-f]{64}$'),
    status text not null
        check(
            status in (
                'SUCCEEDED',
                'FAILED'
            )
        ),
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_document_derivations_owner
    on public.document_derivations(
        owner_id,
        project_id,
        created_at,
        id
    );

create index if not exists idx_document_derivations_document
    on public.document_derivations(
        source_document_id,
        created_at,
        id
    );

alter table public.document_derivations
    enable row level security;

drop policy if exists
    "project_owner_all"
    on public.document_derivations;

create policy
    "project_owner_all"
    on public.document_derivations
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
