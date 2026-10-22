import { Progress } from "@/components/ui/progress";
import type { ResearchTask } from "@/lib/research/schemas";
import { leaseRisk, leaseSecondsRemaining } from "@/lib/research/leases";

export function TaskLeasePanel({ task, claimed }: { task?: ResearchTask; claimed: boolean }) {
  const remaining = leaseSecondsRemaining(task);
  const risk = leaseRisk(task);
  return (
    <section className="sg-lease-panel" aria-live="polite">
      <h3>Lease state</h3>
      <p>{claimed ? "This tab has an in-memory worker identity for the claimed task." : "No local claim is active."}</p>
      {remaining !== undefined ? <Progress label="Lease time remaining" value={Math.min(100, Math.round((remaining / 300) * 100))} /> : null}
      <p>Lease risk: {risk}</p>
    </section>
  );
}
