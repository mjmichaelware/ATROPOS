export type OperationState =
  | "QUEUED"
  | "CLAIMED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCEL_REQUESTED"
  | "CANCELLED"
  | "TIMED_OUT";

export const TERMINAL_OPERATION_STATES = new Set<OperationState>([
  "SUCCEEDED",
  "FAILED",
  "CANCELLED",
  "TIMED_OUT",
]);

export type OperationLike = {
  state: OperationState;
};

const NON_TERMINAL_STATE_COPY: Record<string, string> = {
  QUEUED: "queued — waiting for a worker to pick it up",
  CLAIMED: "claimed by a worker, starting shortly",
  RUNNING: "running",
  CANCEL_REQUESTED: "cancellation requested, finishing the current step first",
};

// Extraction, plan synthesis/verification, export creation/verification,
// and execution run start/verify are all async operations processed by a
// worker on its own schedule (not inline), so this can legitimately run
// for minutes with nothing to show but a spinner unless every poll tick
// surfaces the operation's real state - without it, a working submission
// and a genuinely stuck one look identical to the user.
export function describeOperationProgress(verb: string, operation: Record<string, unknown>): string {
  const state = String(operation.state ?? "QUEUED");
  const base = `${verb} ${NON_TERMINAL_STATE_COPY[state] ?? state.toLowerCase()}.`;
  const current = operation.progress_current;
  const total = operation.progress_total;
  if (typeof current === "number" && typeof total === "number" && total > 0) {
    return `${base} (${current}/${total})`;
  }
  const phase = operation.phase;
  return typeof phase === "string" && phase ? `${base} Phase: ${phase}.` : base;
}
