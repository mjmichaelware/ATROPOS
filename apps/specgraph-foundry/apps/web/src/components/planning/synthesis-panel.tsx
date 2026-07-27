"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

export function SynthesisPanel({ pending, onSynthesize }: { pending: boolean; onSynthesize: (allowOpenResearch: boolean) => Promise<void> }) {
  const [allowOpenResearch, setAllowOpenResearch] = useState<boolean | undefined>(undefined);
  return (
    <div className="sg-planning-form" aria-label="Synthesize a new plan">
      <fieldset className="sg-field-group">
        <legend>Open research allowance</legend>
        <label>
          <input type="radio" name="allow-open-research" checked={allowOpenResearch === false} onChange={() => setAllowOpenResearch(false)} />
          Require all research resolved before synthesis (safer default)
        </label>
        <label>
          <input type="radio" name="allow-open-research" checked={allowOpenResearch === true} onChange={() => setAllowOpenResearch(true)} />
          Allow synthesis despite unresolved research dimensions
        </label>
      </fieldset>
      {allowOpenResearch === true ? (
        <Alert tone="warning" title="Open research does not resolve unresolved work">
          <p>Allowing open research only permits synthesis despite unresolved dimensions. It does not resolve them. Complete the remaining research from the Research workspace when possible.</p>
        </Alert>
      ) : null}
      <Button type="button" loading={pending} disabled={pending || allowOpenResearch === undefined} onClick={() => void onSynthesize(allowOpenResearch === true)}>
        Synthesize plan
      </Button>
    </div>
  );
}
