export const PERFORMANCE_REQUIREMENTS = {
  responsiveInputBudgetMs: 100,
  maxVisibleRows: 250,
  backgroundUpdateBudgetMs: 50,
} as const;

// `limit: number`, not the inferred default. PERFORMANCE_REQUIREMENTS is `as
// const`, so an unannotated default narrows the parameter to the literal 250
// and every caller passing any other bound fails to typecheck.
export function boundedVisibleRows<T>(
  rows: readonly T[],
  limit: number = PERFORMANCE_REQUIREMENTS.maxVisibleRows
): readonly T[] {
  if (!Number.isInteger(limit) || limit < 1) return [];
  return rows.slice(0, limit);
}

export function isWithinUpdateBudget(
  durationMs: number,
  budgetMs: number = PERFORMANCE_REQUIREMENTS.backgroundUpdateBudgetMs
): boolean {
  return Number.isFinite(durationMs) && durationMs >= 0 && durationMs <= budgetMs;
}
