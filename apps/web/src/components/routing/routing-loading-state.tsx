import { SkeletonCard, SkeletonHero, SkeletonTabs } from "@/components/ui/loading-skeleton";

export function RoutingLoadingState() {
  return (
    <section aria-label="Loading routing workspace" aria-busy="true">
      <SkeletonHero />
      <SkeletonTabs count={3} />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-3)" }}>
        <SkeletonCard wide />
        <SkeletonCard />
        <SkeletonCard wide />
      </div>
    </section>
  );
}
