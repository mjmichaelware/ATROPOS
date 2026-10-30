import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

export function SourceErrorState({ title = "Source workspace unavailable", onRetry }: { title?: string; onRetry?: () => void }) {
  return (
    <Alert tone="danger" title={title}>
      <p>The API returned a safe error. Ownership-sensitive resources remain hidden.</p>
      {onRetry ? <Button type="button" variant="secondary" onClick={onRetry}>Retry</Button> : null}
    </Alert>
  );
}
