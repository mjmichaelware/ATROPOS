"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { validateEvidence } from "@/lib/research/evidence";
import type { EvidenceInput } from "@/lib/research/schemas";

const initial: EvidenceInput = { source_uri: "", source_title: "", excerpt: "", evidence_type: "DOCUMENTATION", reliability: 0.8 };

export function EvidenceForm({ disabled, onSubmit }: { disabled?: boolean; onSubmit: (input: EvidenceInput) => Promise<void> }) {
  const [input, setInput] = useState(initial);
  const [errors, setErrors] = useState<Partial<Record<keyof EvidenceInput, string>>>({});
  const [pending, setPending] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const nextErrors = validateEvidence(input);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setPending(true);
    try {
      await onSubmit(input);
      setInput(initial);
    } finally {
      setPending(false);
    }
  }
  return (
    <form className="sg-research-form" onSubmit={(event) => void submit(event)}>
      <Field id="evidence-url" label="Evidence URL" value={input.source_uri} error={errors.source_uri} onChange={(event) => setInput({ ...input, source_uri: event.target.value })} />
      <Field id="evidence-title" label="Evidence title" value={input.source_title} error={errors.source_title} onChange={(event) => setInput({ ...input, source_title: event.target.value })} />
      <div className="sg-field">
        <Label htmlFor="evidence-excerpt">Evidence excerpt</Label>
        <Textarea id="evidence-excerpt" value={input.excerpt} errorId={errors.excerpt ? "evidence-excerpt-error" : undefined} onChange={(event) => setInput({ ...input, excerpt: event.target.value })} />
        {errors.excerpt ? <p id="evidence-excerpt-error" className="sg-field-error">{errors.excerpt}</p> : null}
      </div>
      <Button type="submit" loading={pending} disabled={disabled}>Record evidence</Button>
    </form>
  );
}
