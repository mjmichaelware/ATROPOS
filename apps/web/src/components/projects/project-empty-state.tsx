/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 16.2: Strict Segregation of Mock and Production Workspaces.
 *
 * Maintains strict isolation between demonstration data and real repository state.
 * Sample files must never trigger build pipelines, commit hooks, or automated engine tasks,
 * and must vanish completely once a real project is imported.
 *
 * ## Research: Client-Side Ephemeral In-Memory Filesystem Overlays
 * Researches client-side ephemeral in-memory filesystem overlays for demo sandboxing
 * that guarantee zero residual state on physical storage.
 */

import type { Route } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { MockWorkspaceSegregationBarrier } from "./MockWorkspaceSegregationBarrier";

export const projectRoute: Route = "/projects/new" as Route;
export const emptyStateText = "Upload a source document and SpecGraph will extract it into addressable, provenance-tracked atoms — then research, plan, and verify from there.";

export { Card, Button };

export function ProjectEmptyState() {
  const partition = MockWorkspaceSegregationBarrier.getMockPartition();

  return (
    <Card className="sg-card sg-graph-empty" data-partition={partition.partitionId}>
      <span className="sg-graph-empty-icon" aria-hidden="true">
        ◈
      </span>
      <h2>Your first project starts here</h2>
      <p>{emptyStateText}</p>
      <Button asChild>
        <Link href={projectRoute}>Create project</Link>
      </Button>
    </Card>
  );
}
