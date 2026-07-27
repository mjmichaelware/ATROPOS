"use client";

import { useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
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
    return <ProjectErrorState onRetry={() => void query.refetch()} />;
  }
  const projects = query.data?.body.items ?? [];
  if (projects.length === 0) {
    return <ProjectEmptyState />;
  }
  return (
    <section aria-labelledby="projects-title">
      <h1 id="projects-title">Projects</h1>
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
