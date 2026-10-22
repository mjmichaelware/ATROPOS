import type { Route } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { projectGraphRoute, projectHandoffRoute, projectResearchRoute, projectSourcesRoute } from "@/components/navigation/routes";
import { nextActionLabel, nextActionRoute, READINESS_STAGE_LABELS, readinessLabel, readinessNextAction, readinessTone, stageTone } from "@/lib/projects/readiness";

type ReadinessStage = { name: string; status: string; count?: number; open_dimensions?: number };
type ReadinessData = { status?: string; next_action?: string; stages?: ReadinessStage[] };

function stageRoute(name: string, projectId: string): Route | undefined {
  switch (name) {
    case "SOURCE":
    case "ATOMS":
      return projectSourcesRoute(projectId);
    case "RESEARCH":
      return projectResearchRoute(projectId);
    case "PLANNING":
      return projectGraphRoute(projectId);
    case "INTEGRATION":
      return `${projectHandoffRoute(projectId)}?tab=bindings` as Route;
    case "EXPORT":
      return `${projectHandoffRoute(projectId)}?tab=exports` as Route;
    case "EXECUTION":
      return `${projectHandoffRoute(projectId)}?tab=runs` as Route;
    default:
      return undefined;
  }
}

export function ProjectReadiness({ projectId, readiness }: { projectId: string; readiness: ReadinessData | undefined }) {
  const status = String(readiness?.status ?? "UNKNOWN");
  const stages = readiness?.stages ?? [];
  const ctaHref = nextActionRoute(readiness?.next_action, {
    sources: projectSourcesRoute(projectId),
    research: projectResearchRoute(projectId),
    graph: projectGraphRoute(projectId),
    handoffExports: `${projectHandoffRoute(projectId)}?tab=exports`,
    handoffBindings: `${projectHandoffRoute(projectId)}?tab=bindings`,
    handoffRuns: `${projectHandoffRoute(projectId)}?tab=runs`,
  });

  return (
    <section className="sg-card" aria-labelledby="readiness-title">
      <h2 id="readiness-title">Pipeline readiness</h2>
      <StatusBadge tone={readinessTone(status)} label={readinessLabel(status)} />
      <p>{readinessNextAction(status)}</p>
      {ctaHref ? (
        <Button asChild variant="primary">
          <Link href={ctaHref as Route}>{nextActionLabel(readiness?.next_action)} →</Link>
        </Button>
      ) : null}
      {stages.length > 0 ? (
        <ol className="sg-readiness-stages" aria-label="Source-to-execution pipeline stages">
          {stages.map((stage) => {
            const href = stageRoute(stage.name, projectId);
            const label = READINESS_STAGE_LABELS[stage.name] ?? stage.name;
            const count = typeof stage.count === "number" ? stage.count : typeof stage.open_dimensions === "number" ? stage.open_dimensions : undefined;
            const body = (
              <>
                <StatusBadge tone={stageTone(stage.status)} label={stage.status} />
                <span>{label}</span>
                {typeof count === "number" ? <small className="sg-tabular sg-muted">{stage.open_dimensions !== undefined ? `${count} open` : count}</small> : null}
              </>
            );
            return <li key={stage.name}>{href ? <Link href={href}>{body}</Link> : body}</li>;
          })}
        </ol>
      ) : null}
    </section>
  );
}
