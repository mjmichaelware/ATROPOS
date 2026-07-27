import Link from "next/link";
import { StatusBadge } from "@/components/ui/status-badge";
import { projectTaskRoute } from "@/components/navigation/routes";
import { leaseRisk } from "@/lib/research/leases";
import type { ResearchTask } from "@/lib/research/schemas";
import { taskStatusTone } from "@/lib/research/status";

export function TaskCard({ projectId, task }: { projectId: string; task: ResearchTask }) {
  const status = String(task.status ?? "UNKNOWN");
  const risk = leaseRisk(task);
  return (
    <article className="sg-task-card">
      <div>
        <h3><Link href={projectTaskRoute(projectId, task.id)}>Task {task.dimension ?? task.id}</Link></h3>
        <p>Atom {String(task.atom_id ?? "unavailable")}</p>
      </div>
      <StatusBadge tone={taskStatusTone(status)} label={status} />
      {risk !== "none" ? <small>Lease {risk}</small> : null}
    </article>
  );
}
