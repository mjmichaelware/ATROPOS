import { SkeletonHero, SkeletonParagraph } from "@/components/ui/loading-skeleton";

export default function ResearchTaskLoading() {
  return (
    <section aria-label="Loading task" aria-busy="true">
      <SkeletonHero />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-5)" }}>
        <SkeletonParagraph lines={3} />
        <SkeletonParagraph lines={4} />
      </div>
    </section>
  );
}
