"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Label } from "@/components/ui/label";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Textarea } from "@/components/ui/textarea";
import { validateConclusion } from "@/lib/research/conclusions";
import type { ConclusionInput } from "@/lib/research/schemas";

export function ConclusionForm({
  disabled,
  evidenceIds,
  onSubmit,
}: {
  disabled?: boolean;
  evidenceIds: string[];
  onSubmit: (input: ConclusionInput) => Promise<void>;
}) {
  const [input, setInput] = useState<ConclusionInput>({ conclusion: "", applicability: "APPLICABLE", confidence: 0.8, evidence_ids: evidenceIds });
  const [errors, setErrors] = useState<Partial<Record<keyof ConclusionInput, string>>>({});
  const [pending, setPending] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const candidate = { ...input, evidence_ids: evidenceIds };
    const nextErrors = validateConclusion(candidate);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setPending(true);
    try {
      await onSubmit(candidate);
    } finally {
      setPending(false);
    }
  }
  return (
    <form className="sg-research-form" onSubmit={(event) => void submit(event)}>
      <SegmentedControl label="Applicability" value={input.applicability} onChange={(applicability) => setInput({ ...input, applicability })} options={[{ value: "APPLICABLE", label: "Resolved" }, { value: "NOT_APPLICABLE", label: "Not applicable" }]} />
      {input.applicability === "NOT_APPLICABLE" ? <p className="sg-warning-text">NOT_APPLICABLE needs explicit justification. It is not a shortcut for unknown or unresolved work.</p> : null}
      <div className="sg-field">
        <Label htmlFor="research-conclusion">Conclusion or justification</Label>
        <Textarea id="research-conclusion" value={input.conclusion} errorId={errors.conclusion ? "research-conclusion-error" : undefined} onChange={(event) => setInput({ ...input, conclusion: event.target.value })} />
        {errors.conclusion ? <p id="research-conclusion-error" className="sg-field-error">{errors.conclusion}</p> : null}
      </div>
      <Field id="research-confidence" label="Confidence" type="number" min="0" max="1" step="0.01" value={input.confidence} error={errors.confidence} onChange={(event) => setInput({ ...input, confidence: Number(event.target.value) })} />
      {errors.evidence_ids ? <p className="sg-field-error">{errors.evidence_ids}</p> : null}
      <Button type="submit" loading={pending} disabled={disabled}>Queue completion</Button>
    </form>
  );
}
