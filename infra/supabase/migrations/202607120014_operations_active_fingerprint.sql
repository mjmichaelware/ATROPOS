-- The original unique(owner_id, operation_type, fingerprint) constraint
-- applies across every state, including terminal ones. Several operation
-- types (synthesize_project_plan, verify_plan, verify_export) have a
-- request shape that is legitimately identical across intentional repeat
-- submissions - e.g. re-synthesizing a project's plan after completing
-- research, or re-verifying after fixing an issue - even though the real
-- server-side state they act on has changed. A table-wide unique
-- constraint blocks a new operation from ever being created once the
-- first one reached a terminal state, silently freezing that plan/export
-- on its very first result forever. Scoping uniqueness to active states
-- only preserves the constraint's real purpose (no duplicate concurrent
-- processing of the same in-flight work) without that side effect.

alter table public.operations
    drop constraint if exists
    operations_owner_id_operation_type_fingerprint_key;

create unique index if not exists
    idx_operations_active_fingerprint
    on public.operations(
        owner_id,
        operation_type,
        fingerprint
    )
    where state in (
        'QUEUED',
        'CLAIMED',
        'RUNNING',
        'CANCEL_REQUESTED'
    );
