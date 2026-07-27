"use client";

import { useEffect } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { projectSections } from "@/components/navigation/routes";
import { CopyableId } from "@/components/ui/copyable-id";
import { describeClientError } from "@/lib/api/errors";
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
    return <ProjectErrorState title="Project not found" detail={describeClientError(project.error)} onRetry={() => void project.refetch()} />;
  }
  const projectBody = project.data?.body;
  const workspaceBody = workspace.data?.body ?? {};
  // getReadiness/getWorkspace both nest the real readiness payload as an
  // object (status/next_action/stages) under a `readiness` key - never a
  // plain string. Passed through untouched here so ProjectReadiness can
  // render the actual pipeline, not a stringified "[object Object]".
  const readinessData = (readiness.data?.body.readiness ?? workspaceBody.readiness) as { status?: string; next_action?: string; stages?: Array<{ name: string; status: string; count?: number; open_dimensions?: number }> } | undefined;
  return (
    <section aria-labelledby="command-title">
      <div className="sg-page-heading">
        <div>
          <h1 id="command-title">{projectBody?.name ?? "Project"}</h1>
          <p>{projectBody?.slug}</p>
          <CopyableId value={projectBody?.id} label="Project ID" />
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
        <ProjectReadiness projectId={projectId} readiness={readinessData} />
        <ProjectCounts workspace={workspaceBody} />
        <ProjectLatest projectId={projectId} workspace={workspaceBody} />
      </div>
      <ProjectOperations operations={operations.data?.body.items ?? []} />
    </section>
  );
}
