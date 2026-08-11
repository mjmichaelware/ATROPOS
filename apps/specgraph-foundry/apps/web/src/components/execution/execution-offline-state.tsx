import { Alert } from "@/components/ui/alert";

export function ExecutionOfflineState() {
  return (
    <Alert tone="warning" title="Network unavailable">
      <p>Execution run verification is paused while offline.</p>
    </Alert>
  );
}
