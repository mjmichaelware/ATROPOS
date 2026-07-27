import type { GapMatrix } from "@/lib/research/schemas";
import { AtomCard } from "./atom-card";
import { ResearchEmptyState } from "./research-empty-state";

export function AtomDirectory({ projectId, matrix }: { projectId: string; matrix?: GapMatrix }) {
  const atoms = matrix?.atoms ?? [];
  if (atoms.length === 0) {
    return <ResearchEmptyState message="No atoms with research dimensions were returned." />;
  }
  return (
    <section aria-label="Atom and dimension directory" className="sg-atom-directory">
      {atoms.map((atom) => <AtomCard key={atom.id} projectId={projectId} atom={atom} />)}
      <p className="sg-muted">This directory is bounded by the API preview. Counts remain separate from preview length.</p>
    </section>
  );
}
