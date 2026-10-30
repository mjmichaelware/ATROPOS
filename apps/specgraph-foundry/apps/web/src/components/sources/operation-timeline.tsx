export function OperationTimeline({ operations = [] }: { operations?: Array<Record<string, unknown>> }) {
  return (
    <ol className="sg-operation-timeline">
      {operations.slice(0, 5).map((operation) => (
        <li key={String(operation.id)}>
          <strong>{String(operation.state ?? "UNKNOWN")}</strong>
          <span>{String(operation.operation_type ?? "operation")}</span>
        </li>
      ))}
    </ol>
  );
}
