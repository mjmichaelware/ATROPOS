import { normalizeDimensionStatus } from "./status";
import type { GapAtom, GapCell, GapMatrix } from "./schemas";

export type GapRow = {
  atom: GapAtom;
  cells: GapCell[];
};

export function gapRows(matrix?: GapMatrix): GapRow[] {
  return (matrix?.atoms ?? []).map((atom) => ({ atom, cells: cellsForAtom(atom) }));
}

export function cellsForAtom(atom: GapAtom): GapCell[] {
  const dimensions = atom.dimensions;
  if (Array.isArray(dimensions)) {
    return dimensions.map((cell) => ({ atom_id: atom.id, ...cell }));
  }
  if (dimensions && typeof dimensions === "object") {
    return Object.entries(dimensions).map(([dimension, value]) =>
      typeof value === "object" && value !== null
        ? { atom_id: atom.id, dimension, ...(value as GapCell) }
        : { atom_id: atom.id, dimension, status: String(value) },
    );
  }
  return [];
}

export function countStatuses(matrix?: GapMatrix) {
  const counts = { open: 0, resolved: 0, notApplicable: 0, unknown: 0 };
  for (const row of gapRows(matrix)) {
    for (const cell of row.cells) {
      const status = normalizeDimensionStatus(cell.status);
      if (status === "OPEN") counts.open += 1;
      else if (status === "RESOLVED") counts.resolved += 1;
      else if (status === "NOT_APPLICABLE") counts.notApplicable += 1;
      else counts.unknown += 1;
    }
  }
  return counts;
}
