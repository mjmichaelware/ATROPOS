const COUNT_KEYS = [
  "sources_count",
  "sections_count",
  "chunks_count",
  "atoms_count",
  "dimensions_count",
  "open_dimensions_count",
  "resolved_dimensions_count",
  "not_applicable_dimensions_count",
  "research_tasks_count",
  "relations_count",
  "plans_count",
  "verified_plans_count",
  "bindings_count",
  "enabled_bindings_count",
  "exports_count",
  "verified_exports_count",
  "execution_runs_count",
  "verified_execution_runs_count",
  "providers_count",
  "renderers_count",
  "route_decisions_count",
];

export function ProjectCounts({ workspace }: { workspace: Record<string, unknown> }) {
  return (
    <section className="sg-card" aria-labelledby="counts-title">
      <h2 id="counts-title">Progress overview</h2>
      <dl className="sg-counts">
        {COUNT_KEYS.map((key) => (
          <div key={key}>
            <dt>{key.replaceAll("_", " ")}</dt>
            <dd>{typeof workspace[key] === "number" ? String(workspace[key]) : "0"}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
