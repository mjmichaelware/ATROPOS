import { Button } from "@/components/ui/button";
import { CommandDock } from "@/components/visual/command-dock";

export function SourceCommandBar({ onUpload, onRefresh }: { onUpload: () => void; onRefresh: () => void }) {
  return (
    <CommandDock>
      <Button type="button" variant="primary" onClick={onUpload}>Upload source</Button>
      <Button type="button" variant="secondary" onClick={onRefresh}>Refresh</Button>
    </CommandDock>
  );
}
