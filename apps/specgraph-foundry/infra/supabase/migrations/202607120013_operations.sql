create table if not exists public.operations (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null
        references auth.users(id)
        on delete cascade,
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    operation_type text not null,
    fingerprint text not null
        check(fingerprint ~ '^[0-9a-f]{64}$'),
    state text not null
        check(
            state in (
                'QUEUED',
                'CLAIMED',
                'RUNNING',
                'SUCCEEDED',
                'FAILED',
                'CANCEL_REQUESTED',
                'CANCELLED',
                'TIMED_OUT'
            )
        ),
    phase text not null,
    progress_current integer not null default 0
        check(progress_current >= 0),
    progress_total integer not null default 1
        check(progress_total >= 1),
    attempt_count integer not null default 0
        check(attempt_count >= 0),
    max_attempts integer not null
        check(max_attempts between 1 and 10),
    worker_id text,
    lease_token_hash text
        check(
            lease_token_hash is null
            or lease_token_hash ~ '^[0-9a-f]{64}$'
        ),
    lease_expires_at timestamptz,
    heartbeat_at timestamptz,
    next_attempt_at timestamptz not null,
    cancel_requested_at timestamptz,
    started_at timestamptz,
    finished_at timestamptz,
    timeout_at timestamptz not null,
    request_json jsonb not null,
    result_json jsonb,
    error_code text,
    error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check(progress_current <= progress_total),
    unique(owner_id, operation_type, fingerprint)
);

create index if not exists idx_operations_owner
    on public.operations(
        owner_id,
        project_id,
        created_at,
        id
    );

create index if not exists idx_operations_claim
    on public.operations(
        state,
        next_attempt_at,
        created_at,
        id
    );

alter table public.operations
    enable row level security;

drop policy if exists
    "operation_owner_select"
    on public.operations;

create policy
    "operation_owner_select"
    on public.operations
    for select
    to authenticated
    using (
        (select auth.uid()) is not null
        and owner_id = (select auth.uid())
    );
