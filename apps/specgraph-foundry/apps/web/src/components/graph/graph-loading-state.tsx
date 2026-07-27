import { Skeleton } from "@/components/ui/skeleton";

export function GraphLoadingState() {
  return <Skeleton style={{ height: "28rem" }} aria-label="Loading graph" />;
}
