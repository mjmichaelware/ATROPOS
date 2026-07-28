"use client";

import { HandoffErrorState } from "@/components/handoff/handoff-error-state";

export default function HandoffError({ reset }: { reset: () => void }) {
  return <HandoffErrorState onRetry={reset} />;
}
