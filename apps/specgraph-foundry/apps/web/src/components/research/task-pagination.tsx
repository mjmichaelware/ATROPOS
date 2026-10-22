import { Button } from "@/components/ui/button";

export function TaskPagination({ canBack, hasNext, onBack, onNext }: { canBack: boolean; hasNext?: boolean; onBack: () => void; onNext: () => void }) {
  return (
    <nav className="sg-pagination" aria-label="Research task pagination">
      <Button type="button" variant="secondary" disabled={!canBack} onClick={onBack}>Previous</Button>
      <Button type="button" variant="secondary" disabled={!hasNext} onClick={onNext}>Next cursor page</Button>
    </nav>
  );
}
