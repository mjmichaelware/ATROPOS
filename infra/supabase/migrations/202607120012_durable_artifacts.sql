insert into storage.buckets(
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values(
    'export-artifacts',
    'export-artifacts',
    false,
    10485760,
    array[
        'application/json',
        'text/markdown',
        'text/plain'
    ]::text[]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create table if not exists public.storage_objects (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null
        references auth.users(id)
        on delete cascade,
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    bucket text not null,
    object_path text not null unique,
    media_type text not null,
    byte_length bigint not null
        check(byte_length >= 0),
    sha256 text not null
        check(sha256 ~ '^[0-9a-f]{64}$'),
    state text not null
        check(
            state in (
                'PENDING',
                'STORED',
                'VERIFIED',
                'INVALID'
            )
        ),
    created_at timestamptz not null default now(),
    verified_at timestamptz
);

create index if not exists idx_storage_objects_owner
    on public.storage_objects(
        owner_id,
        project_id,
        created_at,
        id
    );

create table if not exists public.artifact_manifests (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null
        references auth.users(id)
        on delete cascade,
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    export_id uuid not null unique
        references public.exports(id)
        on delete cascade,
    manifest_version text not null,
    state text not null
        check(
            state in (
                'GENERATED',
                'STORED',
                'VERIFIED',
                'INVALID'
            )
        ),
    aggregate_sha256 text not null
        check(aggregate_sha256 ~ '^[0-9a-f]{64}$'),
    total_bytes bigint not null
        check(total_bytes >= 0),
    artifact_count integer not null
        check(artifact_count > 0),
    manifest_json jsonb not null,
    created_at timestamptz not null default now(),
    verified_at timestamptz
);

create index if not exists idx_artifact_manifests_owner
    on public.artifact_manifests(
        owner_id,
        project_id,
        created_at,
        id
    );

alter table public.storage_objects
    enable row level security;

alter table public.artifact_manifests
    enable row level security;

drop policy if exists
    "project_owner_all"
    on public.storage_objects;

create policy
    "project_owner_all"
    on public.storage_objects
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
    "project_owner_all"
    on public.artifact_manifests;

create policy
    "project_owner_all"
    on public.artifact_manifests
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
    "specgraph_export_artifact_insert"
    on storage.objects;

create policy
    "specgraph_export_artifact_insert"
    on storage.objects
    for insert
    to authenticated
    with check (
        bucket_id = 'export-artifacts'
        and split_part(name, '/', 1) = ((select auth.uid())::text)
        and array_length(string_to_array(name, '/'), 1) = 4
    );

drop policy if exists
    "specgraph_export_artifact_select"
    on storage.objects;

create policy
    "specgraph_export_artifact_select"
    on storage.objects
    for select
    to authenticated
    using (
        bucket_id = 'export-artifacts'
        and split_part(name, '/', 1) = ((select auth.uid())::text)
        and array_length(string_to_array(name, '/'), 1) = 4
    );
