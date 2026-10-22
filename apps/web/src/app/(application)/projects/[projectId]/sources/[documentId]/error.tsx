"use client";

import { SourceErrorState } from "@/components/sources/source-error-state";

export default function SourceDocumentError({ reset }: { reset: () => void }) {
  return <SourceErrorState title="Document inspector unavailable" onRetry={reset} />;
}
