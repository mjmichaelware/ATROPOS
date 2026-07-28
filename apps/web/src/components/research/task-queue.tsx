"use client";

import { useState } from "react";
import type { PageResult, ResearchTask } from "@/lib/research/schemas";
import { ResearchEmptyState } from "./research-empty-state";
import { TaskCard } from "./task-card";
import { TaskFilters, type TaskFilter } from "./task-filters";
import { TaskPagination } from "./task-pagination";

export function TaskQueue({
  projectId,
  page,
  canBack,
  onBack,
  onNext,
}: {
  projectId: string;
  page?: PageResult<ResearchTask>;
  canBack: boolean;
  onBack: () => void;
  onNext: () => void;
}) {
  const [filter, setFilter] = useState<TaskFilter>("ALL");
  const items = page?.body.items ?? [];
  const filtered = filter === "ALL" ? items : items.filter((task) => String(task.status ?? "").toUpperCase() === filter);
  if (items.length === 0) {
    return <ResearchEmptyState />;
  }
  return (
    <section className="sg-task-queue" aria-label="Research task queue">
      <p className="sg-muted">
        One task exists per atom-dimension pair. A task closes when someone — you, or an automated provider configured in Routing — records a real conclusion (Applicable or Not applicable)
        backed by real evidence. Nothing here is guessed; every closed task stays linked to what actually justified it.
      </p>
      <TaskFilters value={filter} onChange={setFilter} />
      {filtered.length === 0 ? (
        <p className="sg-muted">No tasks on this page match the &quot;{filter}&quot; filter — try a different filter or the next page.</p>
      ) : (
        <div className="sg-task-list">
          {filtered.map((task) => <TaskCard key={task.id} projectId={projectId} task={task} />)}
        </div>
      )}
      <TaskPagination canBack={canBack} hasNext={page?.pagination.hasMore} onBack={onBack} onNext={onNext} />
    </section>
  );
}
