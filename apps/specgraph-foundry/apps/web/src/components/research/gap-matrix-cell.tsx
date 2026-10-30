import Link from "next/link";
import { projectTaskRoute } from "@/components/navigation/routes";
import { DimensionStatus } from "./dimension-status";
import type { GapCell } from "@/lib/research/schemas";

export function GapMatrixCell({
  projectId,
  cell,
  onSelect,
}: {
  projectId: string;
  cell: GapCell;
  onSelect?: (cell: GapCell) => void;
}) {
  const dimension = String(cell.dimension ?? cell.dimension_id ?? "Dimension");
  const body = (
    <>
      <span>{dimension}</span>
      <DimensionStatus status={cell.status} />
      {cell.rationale ? <small>{String(cell.rationale)}</small> : null}
    </>
  );
  if (cell.task_id) {
    return (
      <div className="sg-gap-cell-row">
        <button type="button" className="sg-gap-cell" data-status={String(cell.status ?? "UNKNOWN").toLowerCase()} onClick={() => onSelect?.(cell)}>
          {body}
        </button>
        <Link href={projectTaskRoute(projectId, cell.task_id)} className="sg-gap-cell-open" aria-label={`Go to research task for ${dimension}`}>
          {"↗"}
        </Link>
      </div>
    );
  }
  return (
    <button type="button" className="sg-gap-cell" data-status={String(cell.status ?? "UNKNOWN").toLowerCase()} onClick={() => onSelect?.(cell)}>
      {body}
    </button>
  );
}
