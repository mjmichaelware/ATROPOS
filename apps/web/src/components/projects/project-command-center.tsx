"use client";

import { useEffect } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { projectSections } from "@/components/navigation/routes";
import { useProjectCommandCenter } from "@/lib/projects/queries";
import { writeRecentProjectId } from "@/lib/projects/selection";
import { ProjectCounts } from "./project-counts";
import { ProjectErrorState } from "./project-error-state";
import { ProjectLatest } from "./project-latest";
import { ProjectOperations } from "./project-operations";
import { ProjectReadiness } from "./project-readiness";

export function ProjectCommandCenter({ projectId }: { projectId: string }) {
  const { project, workspace, readiness, operations } = useProjectCommandCenter(projectId);
  useEffect(() => {
    writeRecentProjectId(window.localStorage, projectId);
  }, [projectId]);

  if (project.isLoading || workspace.isLoading || readiness.isLoading) {
    return <Skeleton style={{ height: "18rem" }} />;
  }
  if (project.isError) {
    return <ProjectErrorState title="Project not found" onRetry={() => void project.refetch()} />;
  }
  const projectBody = project.data?.body;
  const workspaceBody = workspace.data?.body ?? {};
  const readinessState = String(readiness.data?.body.readiness ?? workspaceBody.readiness ?? "UNKNOWN");
  return (
    <section aria-labelledby="command-title">
      <div className="sg-page-heading">
        <div>
          <h1 id="command-title">{projectBody?.name ?? "Project"}</h1>
          <p>{projectBody?.slug}</p>
        </div>
        <Button type="button" variant="secondary" onClick={() => void Promise.all([project.refetch(), workspace.refetch(), readiness.refetch(), operations.refetch()])}>
          Refresh
        </Button>
        {projectSections
          .filter((section) => section.id !== "overview")
          .map((section, index) => (
            <Button key={section.id} asChild variant={index === 0 ? "primary" : "secondary"}>
              <Link href={section.build(projectId)}>Open {section.label}</Link>
            </Button>
          ))}
      </div>
      <div className="sg-grid sg-bento">
        <ProjectReadiness state={readinessState} />
        <ProjectCounts workspace={workspaceBody} />
        <ProjectLatest workspace={workspaceBody} />
      </div>
      <ProjectOperations operations={operations.data?.body.items ?? []} />
      <p className="sg-muted">Sources, Research, the Graph foundation, Handoff, and Routing are active workspaces. Later visual-polish, accessibility, and deployment surfaces remain deferred.</p>
    </section>
  );
}
