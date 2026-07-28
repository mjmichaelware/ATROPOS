import Link from "next/link";
import { projectRoute } from "@/components/navigation/routes";
import type { Project } from "@/lib/projects/schemas";

export function ProjectCard({ project }: { project: Project }) {
  const created = project.created_at ? new Date(project.created_at).toLocaleDateString() : "Unknown date";
  return (
    <Link className="sg-project-card sg-pressable" href={projectRoute(project.id)}>
      <span className="sg-project-card-icon" aria-hidden="true">
        <svg viewBox="0 0 32 32" width="20" height="20" fill="none">
          <line x1="16" y1="7" x2="8" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          <line x1="16" y1="7" x2="24" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          <line x1="8" y1="23" x2="24" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          <circle cx="16" cy="7" r="3" fill="currentColor" />
          <circle cx="8" cy="23" r="3" fill="currentColor" />
          <circle cx="24" cy="23" r="3" fill="currentColor" />
        </svg>
      </span>
      <div className="sg-project-card-body">
        <h2>{project.name}</h2>
        <p className="sg-project-card-slug sg-mono">{project.slug}</p>
        {project.description ? <p className="sg-project-card-description">{project.description}</p> : null}
        <div className="sg-project-card-footer">
          <small className="sg-tabular sg-muted">Created {created}</small>
          <span className="sg-project-card-open" aria-hidden="true">
            Open project →
          </span>
        </div>
      </div>
    </Link>
  );
}
