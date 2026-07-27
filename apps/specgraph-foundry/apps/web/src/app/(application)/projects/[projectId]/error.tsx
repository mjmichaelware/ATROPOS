"use client";

import { ProjectErrorState } from "@/components/projects/project-error-state";

export default function Error({ reset }: { error: Error; reset: () => void }) {
  return <ProjectErrorState title="Project command center unavailable" onRetry={reset} />;
}
