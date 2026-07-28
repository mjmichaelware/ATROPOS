import { Skeleton } from "@/components/ui/skeleton";
import { SkeletonHero } from "@/components/ui/loading-skeleton";

export function GraphLoadingState() {
  return (
    <section aria-label="Loading graph" aria-busy="true">
      <SkeletonHero />
      <Skeleton style={{ height: "22rem", borderRadius: "var(--sg-radius-md)" }} />
    </section>
  );
}
