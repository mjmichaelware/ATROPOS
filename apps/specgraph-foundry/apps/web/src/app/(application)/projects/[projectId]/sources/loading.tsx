import { SkeletonCard, SkeletonHero } from "@/components/ui/loading-skeleton";

export default function SourcesLoading() {
  return (
    <section aria-label="Loading sources" aria-busy="true">
      <SkeletonHero />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-3)" }}>
        <SkeletonCard wide />
        <SkeletonCard />
        <SkeletonCard wide />
        <SkeletonCard />
        <SkeletonCard />
      </div>
    </section>
  );
}
