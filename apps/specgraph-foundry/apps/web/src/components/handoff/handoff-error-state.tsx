import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

export function HandoffErrorState({ title = "Handoff unavailable", onRetry }: { title?: string; onRetry?: () => void }) {
  return (
    <Alert tone="danger" title={title}>
      <p>The workspace could not load. Authorization and existence remain backend-authoritative.</p>
      {onRetry ? (
        <Button type="button" variant="secondary" onClick={onRetry}>
          Retry
        </Button>
      ) : null}
    </Alert>
  );
}
