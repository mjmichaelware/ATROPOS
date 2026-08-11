import { Alert } from "@/components/ui/alert";

export function GraphOfflineState() {
  return (
    <Alert tone="warning" title="Network unavailable">
      <p>Graph data cannot refresh while offline. Nothing beyond the private query cache is retained locally.</p>
    </Alert>
  );
}
