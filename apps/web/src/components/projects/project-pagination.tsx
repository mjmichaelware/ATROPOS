import { Button } from "@/components/ui/button";

export function ProjectPagination({ canGoBack, hasMore, onBack, onNext }: { canGoBack: boolean; hasMore: boolean; onBack: () => void; onNext: () => void }) {
  return (
    <nav className="sg-pagination" aria-label="Project pages">
      <Button type="button" variant="secondary" disabled={!canGoBack} onClick={onBack}>
        Previous
      </Button>
      <Button type="button" variant="secondary" disabled={!hasMore} onClick={onNext}>
        Next
      </Button>
    </nav>
  );
}
