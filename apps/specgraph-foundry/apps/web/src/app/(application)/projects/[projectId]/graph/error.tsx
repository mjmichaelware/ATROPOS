"use client";

import { GraphErrorState } from "@/components/graph/graph-error-state";

export default function GraphError({ reset }: { reset: () => void }) {
  return <GraphErrorState onRetry={reset} />;
}
