import { SkeletonCard } from "@/components/ui/loading-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

export default function Loading() {
  return (
    <section aria-label="Loading projects" aria-busy="true">
      <Skeleton style={{ width: "8rem", height: "1.5rem", marginBottom: "var(--sg-space-4)" }} />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-3)" }}>
        <SkeletonCard wide />
        <SkeletonCard />
        <SkeletonCard wide />
      </div>
    </section>
  );
}
