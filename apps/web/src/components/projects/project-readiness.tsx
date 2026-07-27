import { StatusBadge } from "@/components/ui/status-badge";
import { readinessLabel, readinessNextAction, readinessTone } from "@/lib/projects/readiness";

export function ProjectReadiness({ state }: { state: string }) {
  return (
    <section className="sg-card" aria-labelledby="readiness-title">
      <h2 id="readiness-title">Readiness</h2>
      <StatusBadge tone={readinessTone(state)} label={readinessLabel(state)} />
      <p>{readinessNextAction(state)}</p>
    </section>
  );
}
