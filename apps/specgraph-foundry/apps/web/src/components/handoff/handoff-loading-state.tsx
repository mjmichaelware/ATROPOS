import { Skeleton } from "@/components/ui/skeleton";

export function HandoffLoadingState() {
  return <Skeleton style={{ height: "24rem" }} aria-label="Loading handoff workspace" />;
}
