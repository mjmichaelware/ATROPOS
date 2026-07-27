import { HudPanel } from "@/components/visual/hud-panel";

export function SourceEmptyState({ onUpload }: { onUpload?: () => void }) {
  return (
    <HudPanel title="No authority sources">
      <div className="sg-source-empty">
        <h2>Upload the first immutable source</h2>
        <p>Private Storage preserves original bytes. Finalization derives text and provenance only after server verification.</p>
        {onUpload ? <button className="sg-text-button" type="button" onClick={onUpload}>Open upload bay</button> : null}
      </div>
    </HudPanel>
  );
}
