import { Alert } from "@/components/ui/alert";

export function SourceOfflineState() {
  return <Alert tone="warning" title="Connection interrupted">Source data was not refreshed. No local file bytes were retained.</Alert>;
}
