create table if not exists public.idempotency_records (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null
        references auth.users(id)
        on delete cascade,
    operation text not null,
    idempotency_key_hash text not null,
    canonical_request_hash text not null,
    state text not null,
    http_status integer,
    response_body_json jsonb,
    resource_type text,
    resource_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null,
    check(
        state in (
            'IN_PROGRESS',
            'SUCCEEDED',
            'FAILED'
        )
    ),
    unique(
        owner_id,
        operation,
        idempotency_key_hash
    )
);

create index if not exists idx_idempotency_lookup
    on public.idempotency_records(
        owner_id,
        operation,
        idempotency_key_hash
    );

create index if not exists idx_idempotency_expiry
    on public.idempotency_records(
        state,
        expires_at
    );

alter table public.idempotency_records
    enable row level security;

drop policy if exists
    "project_owner_all"
    on public.idempotency_records;

create policy
    "project_owner_all"
    on public.idempotency_records
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
