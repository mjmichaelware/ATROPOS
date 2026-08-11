import { SkeletonHero, SkeletonParagraph } from "@/components/ui/loading-skeleton";

export default function SourceDocumentLoading() {
  return (
    <section aria-label="Loading document" aria-busy="true">
      <SkeletonHero />
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sg-space-5)" }}>
        <SkeletonParagraph lines={4} />
        <SkeletonParagraph lines={3} />
        <SkeletonParagraph lines={5} />
      </div>
    </section>
  );
}
