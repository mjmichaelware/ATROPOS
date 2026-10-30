import { Alert } from "@/components/ui/alert";

export function HandoffOfflineState() {
  return (
    <Alert tone="warning" title="Network unavailable">
      <p>Handoff mutations are paused while offline. Bindings, exports, and execution runs cannot be created or verified.</p>
    </Alert>
  );
}
