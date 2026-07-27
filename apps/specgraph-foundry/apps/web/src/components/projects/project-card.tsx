import Link from "next/link";
import { projectRoute } from "@/components/navigation/routes";
import type { Project } from "@/lib/projects/schemas";

export function ProjectCard({ project }: { project: Project }) {
  const created = project.created_at ? new Date(project.created_at).toLocaleDateString() : "Unknown date";
  return (
    <Link className="sg-project-card sg-pressable" href={projectRoute(project.id)}>
      <h2>{project.name}</h2>
      <p>{project.slug}</p>
      {project.description ? <p>{project.description}</p> : null}
      <small className="sg-tabular">Created {created}</small>
    </Link>
  );
}
