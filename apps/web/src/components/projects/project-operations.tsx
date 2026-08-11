import { StatusBadge } from "@/components/ui/status-badge";

export function ProjectOperations({ operations }: { operations: Array<Record<string, unknown>> }) {
  return (
    <section className="sg-card" aria-labelledby="operations-title">
      <h2 id="operations-title">Recent operations</h2>
      {operations.length === 0 ? (
        <p>No recent operations.</p>
      ) : (
        <ul>
          {operations.slice(0, 5).map((operation, index) => (
            <li key={String(operation.id ?? index)}>
              <StatusBadge tone="info" label={String(operation.state ?? "UNKNOWN")} />
              <span>{String(operation.operation_type ?? "operation")}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
