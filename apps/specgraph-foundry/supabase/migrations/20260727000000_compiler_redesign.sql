CREATE TABLE IF NOT EXISTS public.compiler_runs (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    input_fingerprint text NOT NULL,
    output_fingerprint text NOT NULL,
    status text NOT NULL,
    event_log_json jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.document_ir_nodes (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES public.source_documents(id) ON DELETE CASCADE,
    node_id text NOT NULL,
    role text NOT NULL,
    content text NOT NULL,
    byte_start integer NOT NULL,
    byte_end integer NOT NULL,
    line_start integer NOT NULL,
    line_end integer NOT NULL,
    parent_id text,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.statement_ir (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES public.source_documents(id) ON DELETE CASCADE,
    statement_id text NOT NULL,
    exact_quote text NOT NULL,
    canonical_text text NOT NULL,
    byte_start integer NOT NULL,
    byte_end integer NOT NULL,
    line_start integer NOT NULL,
    line_end integer NOT NULL,
    parent_node_id text NOT NULL,
    governing_heading_id text,
    governing_list_item_id text,
    structural_ancestry_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    neighboring_context_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    completeness_state text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.discourse_roles (
    id uuid PRIMARY KEY,
    statement_id text NOT NULL,
    role text NOT NULL,
    confidence double precision NOT NULL DEFAULT 1.0,
    source text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.requirement_candidacies (
    id uuid PRIMARY KEY,
    statement_id text NOT NULL,
    is_candidate boolean NOT NULL,
    actor text NOT NULL,
    trigger_text text,
    ears_pattern text,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.atomic_decompositions (
    id uuid PRIMARY KEY,
    parent_statement_id text NOT NULL,
    child_requirement_id text NOT NULL,
    relation_type text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.orthogonal_types (
    id uuid PRIMARY KEY,
    requirement_id text NOT NULL,
    modality text NOT NULL,
    domain_kind text NOT NULL,
    artifact_target text NOT NULL,
    verification_method text NOT NULL,
    confidence double precision NOT NULL DEFAULT 1.0,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.requirement_quality_findings (
    id uuid PRIMARY KEY,
    requirement_id text NOT NULL,
    severity text NOT NULL,
    code text NOT NULL,
    message text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.artifact_contracts (
    id uuid PRIMARY KEY,
    requirement_id text NOT NULL,
    port_type text NOT NULL,
    artifact_name text NOT NULL,
    schema_version text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.dependency_edges (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    from_requirement_id text NOT NULL,
    to_requirement_id text NOT NULL,
    rule_name text NOT NULL,
    evidence text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.provider_proposals (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    provider_id text NOT NULL,
    model_id text NOT NULL,
    proposal_type text NOT NULL,
    target_id text NOT NULL,
    proposed_value text NOT NULL,
    confidence double precision NOT NULL,
    rationale text NOT NULL,
    prompt_hash text NOT NULL,
    response_hash text NOT NULL,
    acceptance_basis text,
    status text NOT NULL DEFAULT 'PROPOSED',
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.provenance_records (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    entity_id text NOT NULL,
    entity_type text NOT NULL,
    activity_id text NOT NULL,
    activity_type text NOT NULL,
    agent_id text NOT NULL,
    agent_type text NOT NULL,
    relation_type text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.source_authorities (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES public.source_documents(id) ON DELETE CASCADE,
    tier integer NOT NULL,
    version text NOT NULL,
    effective_date text NOT NULL,
    owner text NOT NULL,
    is_approved boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.unresolved_records (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    extraction_run_id uuid REFERENCES public.extraction_runs(id) ON DELETE SET NULL,
    source_sha256 text NOT NULL,
    original_text text NOT NULL,
    byte_start integer NOT NULL,
    byte_end integer NOT NULL,
    line_start integer NOT NULL,
    line_end integer NOT NULL,
    role_candidates_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    failed_rules_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    confidence double precision NOT NULL DEFAULT 0.0,
    missing_axes_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    pass_fingerprint text NOT NULL,
    next_action text NOT NULL DEFAULT 'REVIEW',
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.shacl_validation_results (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    compiler_run_id uuid REFERENCES public.compiler_runs(id) ON DELETE CASCADE,
    shape_version text NOT NULL,
    valid boolean NOT NULL,
    violation_count integer NOT NULL DEFAULT 0,
    violations_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.execution_dag_nodes (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    compiler_run_id uuid REFERENCES public.compiler_runs(id) ON DELETE CASCADE,
    node_id text NOT NULL,
    node_type text NOT NULL,
    label text NOT NULL,
    source_atom_id text,
    acceptance_basis text,
    execution_order integer,
    is_ready boolean NOT NULL DEFAULT false,
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.execution_dag_edges (
    id uuid PRIMARY KEY,
    dag_id uuid NOT NULL REFERENCES public.execution_dag_nodes(id) ON DELETE CASCADE,
    from_node_id text NOT NULL,
    to_node_id text NOT NULL,
    edge_type text NOT NULL,
    acceptance_basis text NOT NULL,
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.requirements_debt (
    id uuid PRIMARY KEY,
    extraction_run_id uuid NOT NULL REFERENCES public.extraction_runs(id) ON DELETE CASCADE,
    unresolved_count integer NOT NULL,
    candidate_count integer NOT NULL,
    ratio double precision NOT NULL,
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.missing_regions (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES public.source_documents(id) ON DELETE CASCADE,
    coordinates_json jsonb NOT NULL,
    detection_method text NOT NULL,
    severity text NOT NULL DEFAULT 'LOW',
    research_task_id uuid,
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.viewpoint_conflicts (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    conflict_type text NOT NULL DEFAULT 'VIEWPOINT_CONFLICT',
    source_span_ids_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    target_span_ids_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    viewpoint_labels_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    conflict_summary text NOT NULL,
    detection_rule text NOT NULL,
    confidence double precision NOT NULL DEFAULT 0.0,
    status text NOT NULL DEFAULT 'OPEN',
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.conflict_policies (
    id uuid PRIMARY KEY,
    conflict_edge_id uuid NOT NULL REFERENCES public.viewpoint_conflicts(id) ON DELETE CASCADE,
    tolerated_until timestamp with time zone,
    escalation_action text NOT NULL DEFAULT 'NOTIFY',
    created_by text NOT NULL,
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.conformal_decisions (
    id uuid PRIMARY KEY,
    target_type text NOT NULL,
    target_id text NOT NULL,
    prediction_set_json jsonb NOT NULL,
    nonconformity_scores_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    calibration_fingerprint text NOT NULL,
    decision text NOT NULL,
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.readiness_certificates (
    id uuid PRIMARY KEY,
    plan_id uuid,
    certificate_json jsonb NOT NULL,
    fingerprint text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.quarantine_ledger (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    quarantined_atom_id text NOT NULL,
    reason_code text NOT NULL,
    original_row_json jsonb NOT NULL,
    replacement_atom_id text,
    migration_activity_id text,
    before_fingerprint text NOT NULL,
    after_fingerprint text,
    quarantined_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE IF NOT EXISTS public.applicability_states (
    id uuid PRIMARY KEY,
    atom_id text NOT NULL,
    state text NOT NULL,
    dimension text NOT NULL DEFAULT 'FUNCTIONAL_CONTRACT',
    evidence_summary text,
    claim_id uuid,
    fingerprint text NOT NULL,
    is_legacy_compat boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now())
);

alter table public.compiler_runs enable row level security;
alter table public.document_ir_nodes enable row level security;
alter table public.statement_ir enable row level security;
alter table public.discourse_roles enable row level security;
alter table public.requirement_candidacies enable row level security;
alter table public.atomic_decompositions enable row level security;
alter table public.orthogonal_types enable row level security;
alter table public.requirement_quality_findings enable row level security;
alter table public.artifact_contracts enable row level security;
alter table public.dependency_edges enable row level security;
alter table public.provider_proposals enable row level security;
alter table public.provenance_records enable row level security;
alter table public.source_authorities enable row level security;
alter table public.unresolved_records enable row level security;
alter table public.shacl_validation_results enable row level security;
alter table public.execution_dag_nodes enable row level security;
alter table public.execution_dag_edges enable row level security;
alter table public.requirements_debt enable row level security;
alter table public.missing_regions enable row level security;
alter table public.viewpoint_conflicts enable row level security;
alter table public.conflict_policies enable row level security;
alter table public.conformal_decisions enable row level security;
alter table public.readiness_certificates enable row level security;
alter table public.quarantine_ledger enable row level security;
alter table public.applicability_states enable row level security;

grant all on all tables in schema public to authenticated;
grant all on all tables in schema public to service_role;
