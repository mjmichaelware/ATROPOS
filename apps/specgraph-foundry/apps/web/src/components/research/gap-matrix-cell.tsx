import { DimensionStatus } from "./dimension-status";
import type { GapCell } from "@/lib/research/schemas";

export function GapMatrixCell({ cell, onSelect }: { cell: GapCell; onSelect?: (cell: GapCell) => void }) {
  const dimension = String(cell.dimension ?? cell.dimension_id ?? "Dimension");
  return (
    <button type="button" className="sg-gap-cell" data-status={String(cell.status ?? "UNKNOWN").toLowerCase()} onClick={() => onSelect?.(cell)}>
      <span>{dimension}</span>
      <DimensionStatus status={cell.status} />
      {cell.rationale ? <small>{String(cell.rationale)}</small> : null}
    </button>
  );
}
