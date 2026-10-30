import { SkeletonCard, SkeletonHero, SkeletonTabs } from "@/components/ui/loading-skeleton";

export function HandoffLoadingState() {
  return (
    <section aria-label="Loading handoff workspace" aria-busy="true">
      <SkeletonHero />
      <SkeletonTabs count={4} />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-3)" }}>
        <SkeletonCard wide />
        <SkeletonCard />
        <SkeletonCard wide />
      </div>
    </section>
  );
}
