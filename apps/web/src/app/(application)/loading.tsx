import { SkeletonCard } from "@/components/ui/loading-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

export default function Loading() {
  return (
    <section aria-label="Loading" aria-busy="true">
      <Skeleton style={{ width: "10rem", height: "1.75rem", marginBottom: "var(--sg-space-4)" }} />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-3)" }}>
        <SkeletonCard wide />
        <SkeletonCard />
      </div>
    </section>
  );
}
