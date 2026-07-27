import { HudPanel } from "@/components/visual/hud-panel";

export function SourceEmptyState({ onUpload }: { onUpload?: () => void }) {
  return (
    <HudPanel title="No documents yet">
      <div className="sg-source-empty">
        <h2>Upload your first source document</h2>
        <p>Your file is stored securely and kept exactly as-is. Once it&apos;s verified, we extract text and structure from it — the original never changes.</p>
        {onUpload ? <button className="sg-text-button" type="button" onClick={onUpload}>Upload a document</button> : null}
      </div>
    </HudPanel>
  );
}
