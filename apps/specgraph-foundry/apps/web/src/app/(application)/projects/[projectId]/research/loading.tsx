import { SkeletonCard, SkeletonHero, SkeletonTabs } from "@/components/ui/loading-skeleton";

export default function ResearchLoading() {
  return (
    <section aria-label="Loading research" aria-busy="true">
      <SkeletonHero />
      <SkeletonTabs count={3} />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-3)" }}>
        <SkeletonCard wide />
        <SkeletonCard />
        <SkeletonCard wide />
        <SkeletonCard />
      </div>
    </section>
  );
}
