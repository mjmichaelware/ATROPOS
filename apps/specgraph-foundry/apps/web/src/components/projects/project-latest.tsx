const LATEST_KEYS = ["latest_document", "latest_plan", "latest_export", "latest_execution_run", "latest_route_decision"];

export function ProjectLatest({ workspace }: { workspace: Record<string, unknown> }) {
  return (
    <section className="sg-card" aria-labelledby="latest-title">
      <h2 id="latest-title">Latest records</h2>
      <ul>
        {LATEST_KEYS.map((key) => (
          <li key={key}>
            <strong>{key.replaceAll("_", " ")}</strong>: {workspace[key] ? "Available" : "None yet"}
          </li>
        ))}
      </ul>
    </section>
  );
}
