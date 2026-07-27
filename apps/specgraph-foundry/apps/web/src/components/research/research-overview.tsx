import Link from "next/link";
import { projectRoutingRoute } from "@/components/navigation/routes";
import { HudPanel } from "@/components/visual/hud-panel";
import { numberValue } from "@/lib/research/status";
import type { GapMatrix, ResearchWorkspace } from "@/lib/research/schemas";
import { AuthoritySeparation } from "./authority-separation";
import { GapSummary } from "./gap-summary";

export function ResearchOverview({ projectId, workspace, matrix }: { projectId: string; workspace?: ResearchWorkspace; matrix?: GapMatrix }) {
  const counts = workspace?.counts ?? matrix?.summary;
  return (
    <section className="sg-research-overview">
      <GapSummary counts={counts} matrix={matrix} />
      <div className="sg-research-metrics">
        <HudPanel title="Atoms" status={String(numberValue(counts?.atoms))}><p>Individual claims pulled out of your source documents.</p></HudPanel>
        <HudPanel title="Tasks" status={String(numberValue(counts?.tasks))}><p>One task per atom per dimension — each needs a claim before anyone can work it.</p></HudPanel>
        <HudPanel title="Evidence" status={String(numberValue(counts?.evidence))}><p>What backs up every conclusion. Your original sources are never changed to produce it.</p></HudPanel>
        <HudPanel title="Automation" status="Routing">
          <p>
            Free AI providers can answer these automatically — set them up in{" "}
            <Link href={projectRoutingRoute(projectId)}>Routing</Link>.
          </p>
        </HudPanel>
      </div>
      <AuthoritySeparation />
    </section>
  );
}
