import type { PlanBinding } from "@/lib/planning/schemas";

export function BindingPanel({ binding }: { binding: PlanBinding | undefined }) {
  if (!binding) {
    return <p className="sg-muted">No plan binding is available for this node.</p>;
  }
  return (
    <dl>
      <div>
        <dt>Bound atom</dt>
        <dd className="sg-mono">{binding.atom_id}</dd>
      </div>
      {binding.canonical_statement ? (
        <div>
          <dt>Statement</dt>
          <dd>{binding.canonical_statement}</dd>
        </div>
      ) : null}
      {binding.kind ? (
        <div>
          <dt>Kind</dt>
          <dd>{binding.kind}</dd>
        </div>
      ) : null}
      {binding.modality ? (
        <div>
          <dt>Modality</dt>
          <dd>{binding.modality}</dd>
        </div>
      ) : null}
      {typeof binding.sequence_number === "number" ? (
        <div>
          <dt>Sequence</dt>
          <dd>{binding.sequence_number}</dd>
        </div>
      ) : null}
    </dl>
  );
}
