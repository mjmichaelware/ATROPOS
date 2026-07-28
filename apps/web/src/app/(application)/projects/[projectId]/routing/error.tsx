"use client";

import { RoutingErrorState } from "@/components/routing/routing-error-state";

export default function RoutingError({ reset }: { reset: () => void }) {
  return <RoutingErrorState onRetry={reset} />;
}
