import { Progress } from "@/components/ui/progress";
import { operationLabel, operationProgress } from "@/lib/research/operations";

export function TaskOperation({ operation }: { operation?: Record<string, unknown> }) {
  return (
    <section className="sg-task-operation" aria-live="polite">
      <h3>Completion operation</h3>
      <p>{operationLabel(operation)}</p>
      <Progress label="Completion operation progress" value={operationProgress(operation)} />
    </section>
  );
}
