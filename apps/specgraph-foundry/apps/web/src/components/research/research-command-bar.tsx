import { Button } from "@/components/ui/button";
import { CommandDock } from "@/components/visual/command-dock";

export function ResearchCommandBar({ onRefresh }: { onRefresh: () => void }) {
  return (
    <CommandDock>
      <Button type="button" variant="secondary" onClick={onRefresh}>Refresh field</Button>
    </CommandDock>
  );
}
