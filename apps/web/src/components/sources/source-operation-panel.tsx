import { Progress } from "@/components/ui/progress";
import { operationLabel, operationProgress } from "@/lib/sources/operations";

export function SourceOperationPanel({ operations = [] }: { operations?: Array<Record<string, unknown>> }) {
  return (
    <section className="sg-operation-panel" aria-labelledby="source-operations-title">
      <h2 id="source-operations-title">Ingestion operations</h2>
      {operations.length === 0 ? <p>No source operations are currently visible.</p> : null}
      {operations.slice(0, 5).map((operation) => (
        <article key={String(operation.id)} className="sg-operation-row">
          <strong>{operationLabel(operation)}</strong>
          <Progress value={operationProgress(operation)} label={`${operationLabel(operation)} progress`} />
        </article>
      ))}
    </section>
  );
}
