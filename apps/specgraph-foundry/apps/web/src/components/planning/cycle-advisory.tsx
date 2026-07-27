import { Alert } from "@/components/ui/alert";
import type { CycleCheckResult } from "@/lib/planning/cycle";

export function CycleAdvisory({ result }: { result: CycleCheckResult | undefined }) {
  if (!result) {
    return null;
  }
  if (result.kind === "not-applicable") {
    return (
      <Alert tone="info" title="Cycle check not applicable">
        <p>{result.reason}</p>
      </Alert>
    );
  }
  if (result.kind === "no-cycle-in-loaded-subset") {
    return (
      <Alert tone="info" title="No cycle found in the loaded subset">
        <p>The currently loaded relations show no dependency cycle for this proposal. The server independently validates the full project graph before accepting it.</p>
      </Alert>
    );
  }
  return (
    <Alert tone="danger" title="Cycle detected in the loaded subset">
      <p>This relation would close a REQUIRES dependency cycle among currently loaded atoms:</p>
      <ol className="sg-cycle-path" aria-label="Cycle path">
        {result.path.map((atomId, index) => (
          <li key={`${atomId}-${index}`}>
            <span className="sg-mono">{atomId}</span>
          </li>
        ))}
      </ol>
      <p>This is an advisory preview over loaded data only. The server performs the authoritative cycle check on submission.</p>
    </Alert>
  );
}
