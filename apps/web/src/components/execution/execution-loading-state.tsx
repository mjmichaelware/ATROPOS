import { Skeleton } from "@/components/ui/skeleton";

export function ExecutionLoadingState() {
  return <Skeleton style={{ height: "24rem" }} aria-label="Loading execution run" />;
}
