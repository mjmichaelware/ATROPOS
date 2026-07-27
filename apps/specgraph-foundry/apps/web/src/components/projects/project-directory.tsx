"use client";

import { useState } from "react";
import { DataGrid } from "@/components/visual/data-grid";
import { Skeleton } from "@/components/ui/skeleton";
import { describeClientError } from "@/lib/api/errors";
import { useProjectsPage } from "@/lib/projects/queries";
import { ProjectCard } from "./project-card";
import { ProjectEmptyState } from "./project-empty-state";
import { ProjectErrorState } from "./project-error-state";
import { ProjectPagination } from "./project-pagination";

export function ProjectDirectory() {
  const [cursorStack, setCursorStack] = useState<string[]>([]);
  const cursor = cursorStack.at(-1);
  const query = useProjectsPage(cursor, cursorStack.length);

  if (query.isLoading) {
    return <Skeleton style={{ height: "14rem" }} />;
  }
  if (query.isError) {
    return <ProjectErrorState detail={describeClientError(query.error)} onRetry={() => void query.refetch()} />;
  }
  const projects = query.data?.body.items ?? [];
  if (projects.length === 0) {
    return <ProjectEmptyState />;
  }
  return (
    <section className="sg-source-workspace" aria-labelledby="projects-title">
      <DataGrid />
      <header className="sg-source-hero">
        <p className="sg-micro-label">Command center</p>
        <h1 id="projects-title">Projects</h1>
        <p>Every source-to-verified-execution pipeline you&rsquo;re running, in one place. Open one to pick up research, planning, or handoff exactly where you left off.</p>
      </header>
      <div className="sg-project-grid">
        {projects.map((project) => (
          <ProjectCard key={project.id} project={project} />
        ))}
      </div>
      <ProjectPagination
        canGoBack={cursorStack.length > 0}
        hasMore={query.data?.pagination.hasMore ?? false}
        onBack={() => setCursorStack((stack) => stack.slice(0, -1))}
        onNext={() => {
          const next = query.data?.pagination.nextCursor;
          if (next) {
            setCursorStack((stack) => [...stack, next]);
          }
        }}
      />
    </section>
  );
}
