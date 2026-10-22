import { Alert } from "@/components/ui/alert";

export function RoutingOfflineState() {
  return (
    <Alert tone="warning" title="Network unavailable">
      <p>Routing policy, provider, renderer, and unlock changes are paused while offline.</p>
    </Alert>
  );
}
