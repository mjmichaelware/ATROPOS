import { SkeletonCard, SkeletonHero, SkeletonParagraph } from "@/components/ui/loading-skeleton";

export function ExecutionLoadingState() {
  return (
    <section aria-label="Loading execution run" aria-busy="true">
      <SkeletonHero />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-4)" }}>
        <SkeletonParagraph lines={2} />
        <SkeletonCard wide />
        <SkeletonCard />
        <SkeletonCard wide />
      </div>
    </section>
  );
}
