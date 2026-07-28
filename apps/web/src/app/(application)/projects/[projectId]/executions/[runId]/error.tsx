"use client";

import { ExecutionErrorState } from "@/components/execution/execution-error-state";

export default function ExecutionRunError({ reset }: { reset: () => void }) {
  return <ExecutionErrorState onRetry={reset} />;
}
