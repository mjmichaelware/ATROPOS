import { HudPanel } from "@/components/visual/hud-panel";
import { HashDisplay } from "./hash-display";
import type { DerivationSummary } from "@/lib/sources/schemas";

export function DocumentDerivation({ derivation }: { derivation?: DerivationSummary | null }) {
  if (!derivation) {
    return <HudPanel title="Derivation"><p>No derivation metadata is available yet.</p></HudPanel>;
  }
  return (
    <HudPanel title="Derived text" status={derivation.adapter_name}>
      <dl className="sg-authority-grid">
        <div><dt>Detected media</dt><dd>{derivation.detected_media_type}</dd></div>
        <div><dt>Adapter</dt><dd>{derivation.adapter_name} {derivation.adapter_version}</dd></div>
        <div><dt>Derived bytes</dt><dd>{Number(derivation.derived_byte_count ?? 0).toLocaleString()}</dd></div>
      </dl>
      <HashDisplay value={derivation.derived_sha256} label="Derived SHA-256" />
    </HudPanel>
  );
}
