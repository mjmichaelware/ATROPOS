import type { GapMatrix } from "@/lib/research/schemas";
import { AtomCard } from "./atom-card";
import { ResearchEmptyState } from "./research-empty-state";

export function AtomDirectory({ projectId, matrix }: { projectId: string; matrix?: GapMatrix }) {
  const atoms = matrix?.atoms ?? [];
  if (atoms.length === 0) {
    return <ResearchEmptyState message="No atoms have research dimensions to show yet." />;
  }
  return (
    <section aria-label="Atom and dimension directory" className="sg-atom-directory">
      {atoms.map((atom) => <AtomCard key={atom.id} projectId={projectId} atom={atom} />)}
      <p className="sg-muted">Showing a preview of the full list — the counts above always reflect everything, not just what&apos;s listed here.</p>
    </section>
  );
}
