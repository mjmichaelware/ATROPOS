export function operationProgress(operation?: Record<string, unknown>) {
  const current = Number(operation?.progress_current ?? 0);
  const total = Number(operation?.progress_total ?? 0);
  return total > 0 ? Math.round((current / total) * 100) : undefined;
}

export function operationLabel(operation?: Record<string, unknown>) {
  if (!operation) {
    return "No active operation";
  }
  return `${String(operation.operation_type ?? "operation")} ${String(operation.state ?? "")}`.trim();
}
