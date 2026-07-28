"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Tooltip } from "@/components/ui/tooltip";

export function SynthesisPanel({ pending, onSynthesize }: { pending: boolean; onSynthesize: (allowOpenResearch: boolean) => Promise<void> }) {
  const [allowOpenResearch, setAllowOpenResearch] = useState<boolean | undefined>(undefined);
  return (
    <div className="sg-planning-form" aria-label="Turn your research into a plan">
      <fieldset className="sg-field-group">
        <legend>
          Ready to build a plan?
          <Tooltip label="A plan is a step-by-step execution map generated from everything your research has confirmed so far.">
            <button type="button" className="sg-help-hint" aria-label="What does this mean?">
              ?
            </button>
          </Tooltip>
        </legend>
        <label>
          <input type="radio" name="allow-open-research" checked={allowOpenResearch === false} onChange={() => setAllowOpenResearch(false)} />
          Wait until every research task is finished <span className="sg-muted">(safer, most complete result)</span>
        </label>
        <label>
          <input type="radio" name="allow-open-research" checked={allowOpenResearch === true} onChange={() => setAllowOpenResearch(true)} />
          Build it now with what&apos;s done so far
        </label>
      </fieldset>
      {allowOpenResearch === true ? (
        <Alert tone="warning" title="This gives you a preview, not a finished plan">
          <p>Steps tied to unfinished research will show up marked as blocked. Nothing gets skipped or guessed — finish the remaining research from the Research workspace and re-synthesize to fill them in.</p>
        </Alert>
      ) : null}
      <Button type="button" loading={pending} disabled={pending || allowOpenResearch === undefined} onClick={() => void onSynthesize(allowOpenResearch === true)}>
        {pending ? "Building your plan…" : "Build plan"}
      </Button>
    </div>
  );
}
