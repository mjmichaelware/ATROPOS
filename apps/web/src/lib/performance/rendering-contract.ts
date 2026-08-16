export const PERFORMANCE_REQUIREMENTS = {
  responsiveInputBudgetMs: 100,
  maxVisibleRows: 250,
  backgroundUpdateBudgetMs: 50,
} as const;

export function boundedVisibleRows<T>(rows: readonly T[], limit = PERFORMANCE_REQUIREMENTS.maxVisibleRows): readonly T[] {
  if (!Number.isInteger(limit) || limit < 1) return [];
  return rows.slice(0, limit);
}

export function isWithinUpdateBudget(durationMs: number, budgetMs = PERFORMANCE_REQUIREMENTS.backgroundUpdateBudgetMs): boolean {
  return Number.isFinite(durationMs) && durationMs >= 0 && durationMs <= budgetMs;
}
