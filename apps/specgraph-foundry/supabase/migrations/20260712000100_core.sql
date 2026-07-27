create extension if not exists pgcrypto;

create table if not exists public.projects (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique,
    name text not null,
    description text not null default '',
    created_at timestamptz not null default now()
);

create table if not exists public.source_documents (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    title text not null,
    sha256 text not null,
    byte_count bigint not null,
    line_count bigint not null,
    storage_path text,
    content text,
    created_at timestamptz not null default now(),
    unique(project_id, sha256)
);

create table if not exists public.graphs (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null
        references public.projects(id)
        on delete cascade,
    name text not null,
    kind text not null,
    enforce_acyclic boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists public.graph_nodes (
    id uuid primary key default gen_random_uuid(),
    graph_id uuid not null
        references public.graphs(id)
        on delete cascade,
    node_key text not null,
    node_type text not null,
    title text not null,
    status text not null,
    payload_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique(graph_id, node_key)
);

create table if not exists public.graph_edges (
    id uuid primary key default gen_random_uuid(),
    graph_id uuid not null
        references public.graphs(id)
        on delete cascade,
    from_node_id uuid not null
        references public.graph_nodes(id)
        on delete cascade,
    to_node_id uuid not null
        references public.graph_nodes(id)
        on delete cascade,
    edge_type text not null,
    inferred boolean not null default false,
    rationale text not null default '',
    created_at timestamptz not null default now(),
    check(from_node_id <> to_node_id),
    unique(
        graph_id,
        from_node_id,
        to_node_id,
        edge_type
    )
);

create index if not exists idx_graph_nodes_status
    on public.graph_nodes(graph_id, status);

create index if not exists idx_graph_edges_from
    on public.graph_edges(graph_id, from_node_id);

create index if not exists idx_graph_edges_to
    on public.graph_edges(graph_id, to_node_id);

alter table public.projects enable row level security;
alter table public.source_documents enable row level security;
alter table public.graphs enable row level security;
alter table public.graph_nodes enable row level security;
alter table public.graph_edges enable row level security;
