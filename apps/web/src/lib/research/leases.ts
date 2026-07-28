import type { ResearchTask } from "./schemas";

export function leaseSecondsRemaining(task?: Pick<ResearchTask, "lease_expires_at">, now = Date.now()) {
  if (!task?.lease_expires_at) {
    return undefined;
  }
  const expires = Date.parse(task.lease_expires_at);
  if (!Number.isFinite(expires)) {
    return undefined;
  }
  return Math.max(0, Math.ceil((expires - now) / 1000));
}

export function leaseRisk(task?: Pick<ResearchTask, "lease_expires_at">, now = Date.now()) {
  const remaining = leaseSecondsRemaining(task, now);
  if (remaining === undefined) return "none";
  if (remaining <= 0) return "lost";
  if (remaining <= 45) return "near-expiry";
  return "active";
}
