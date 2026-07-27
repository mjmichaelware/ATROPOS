import { HudPanel } from "@/components/visual/hud-panel";
import { numberValue } from "@/lib/research/status";
import type { GapMatrix, ResearchWorkspace } from "@/lib/research/schemas";
import { AuthoritySeparation } from "./authority-separation";
import { GapSummary } from "./gap-summary";

export function ResearchOverview({ workspace, matrix }: { workspace?: ResearchWorkspace; matrix?: GapMatrix }) {
  const counts = workspace?.counts ?? matrix?.summary;
  return (
    <section className="sg-research-overview">
      <GapSummary counts={counts} matrix={matrix} />
      <div className="sg-research-metrics">
        <HudPanel title="Atoms" status={String(numberValue(counts?.atoms))}><p>Atom dimensions come from backend extraction.</p></HudPanel>
        <HudPanel title="Tasks" status={String(numberValue(counts?.tasks))}><p>Claiming and completion are API-controlled.</p></HudPanel>
        <HudPanel title="Evidence" status={String(numberValue(counts?.evidence))}><p>Evidence supports conclusions but never mutates source authority.</p></HudPanel>
      </div>
      <AuthoritySeparation />
    </section>
  );
}
