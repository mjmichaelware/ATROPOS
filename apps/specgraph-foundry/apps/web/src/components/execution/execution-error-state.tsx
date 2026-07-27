import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

export function ExecutionErrorState({ title = "Execution run unavailable", onRetry }: { title?: string; onRetry?: () => void }) {
  return (
    <Alert tone="danger" title={title}>
      <p>This execution run couldn&apos;t be loaded — it may not exist, or you may not have access to it.</p>
      {onRetry ? (
        <Button type="button" variant="secondary" onClick={onRetry}>
          Retry
        </Button>
      ) : null}
    </Alert>
  );
}
