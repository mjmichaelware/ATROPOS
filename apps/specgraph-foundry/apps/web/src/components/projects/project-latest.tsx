import type { Route } from "next";
import Link from "next/link";
import { projectDocumentRoute, projectGraphRoute, projectHandoffRoute } from "@/components/navigation/routes";

type LatestRecord = Record<string, unknown> | null | undefined;

function describeRecord(name: string, record: LatestRecord): string {
  if (!record) return "None yet";
  switch (name) {
    case "document":
      return String(record.title ?? "Untitled source");
    case "plan":
      return String(record.status ?? "UNKNOWN");
    case "export":
      return String(record.status ?? "UNKNOWN");
    case "execution_run":
      return String(record.status ?? "UNKNOWN");
    case "route_decision":
      return String(record.decision_type ?? "UNKNOWN");
    default:
      return "Available";
  }
}

// Keys and nesting match ProjectWorkspaceService.get()'s `latest` dict
// exactly (src/specgraph_foundry/http_api/workspace.py): every record lives
// under workspace.latest.<name>, not a flat workspace.latest_<name> key.
export function ProjectLatest({ projectId, workspace }: { projectId: string; workspace: Record<string, unknown> }) {
  const latest = (workspace.latest as Record<string, LatestRecord> | undefined) ?? {};
  const rows: Array<{ key: string; label: string; href?: Route }> = [
    { key: "document", label: "Latest source", href: latest.document ? projectDocumentRoute(projectId, String(latest.document.id)) : undefined },
    { key: "plan", label: "Latest plan", href: projectGraphRoute(projectId) },
    { key: "export", label: "Latest export", href: `${projectHandoffRoute(projectId)}?tab=exports` as Route },
    { key: "execution_run", label: "Latest execution run", href: `${projectHandoffRoute(projectId)}?tab=runs` as Route },
    { key: "route_decision", label: "Latest route decision", href: undefined },
  ];
  return (
    <section className="sg-card" aria-labelledby="latest-title">
      <h2 id="latest-title">Latest records</h2>
      <ul>
        {rows.map(({ key, label, href }) => {
          const record = latest[key];
          const description = describeRecord(key, record);
          return (
            <li key={key}>
              <strong>{label}</strong>: {record && href ? <Link href={href}>{description}</Link> : description}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
