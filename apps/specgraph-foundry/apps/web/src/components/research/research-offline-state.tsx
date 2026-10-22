import { Alert } from "@/components/ui/alert";

export function ResearchOfflineState() {
  return (
    <Alert tone="warning" title="Network unavailable">
      <p>Research actions are paused. Existing evidence and conclusions are not cached outside the private query cache.</p>
    </Alert>
  );
}
