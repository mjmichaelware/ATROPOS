import { Button } from "@/components/ui/button";
import { PreviewList } from "./document-sections";

export function DocumentAtoms({ atoms = [], hasMore, onNext }: { atoms?: Array<Record<string, unknown>>; hasMore?: boolean; onNext?: () => void }) {
  return (
    <section>
      <PreviewList title="Atoms" items={atoms} />
      {hasMore ? <Button type="button" variant="secondary" onClick={onNext}>Next atom page</Button> : null}
    </section>
  );
}
