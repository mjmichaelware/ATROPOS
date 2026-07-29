"use client";

import { ResearchErrorState } from "@/components/research/research-error-state";

export default function ResearchError({ reset }: { reset: () => void }) {
  return <ResearchErrorState onRetry={reset} />;
}
