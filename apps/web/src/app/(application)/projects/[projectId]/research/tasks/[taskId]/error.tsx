"use client";

import { ResearchErrorState } from "@/components/research/research-error-state";

export default function ResearchTaskError({ reset }: { reset: () => void }) {
  return <ResearchErrorState title="Research task unavailable" onRetry={reset} />;
}
