"use client";

import { SourceErrorState } from "@/components/sources/source-error-state";

export default function SourcesError({ reset }: { reset: () => void }) {
  return <SourceErrorState onRetry={reset} />;
}
