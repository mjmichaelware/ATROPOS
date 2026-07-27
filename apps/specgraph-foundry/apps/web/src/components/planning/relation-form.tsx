"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { checkProposedRelationCycle } from "@/lib/planning/cycle";
import { validateRelationInput } from "@/lib/planning/relations";
import { RELATION_TYPES, type AuthorityRelation, type RelationInput } from "@/lib/planning/schemas";
import { CycleAdvisory } from "./cycle-advisory";

const initial: RelationInput = { from_atom_id: "", to_atom_id: "", relation_type: "REQUIRES", rationale: "", confidence: undefined };

export function RelationForm({
  atomOptions,
  relations,
  pending,
  onSubmit,
  onCancel,
}: {
  atomOptions: Array<{ id: string; label: string }>;
  relations: AuthorityRelation[];
  pending: boolean;
  onSubmit: (input: RelationInput) => Promise<void>;
  onCancel: () => void;
}) {
  const [input, setInput] = useState<RelationInput>(initial);
  const [errors, setErrors] = useState<Partial<Record<keyof RelationInput, string>>>({});

  const cycleResult = useMemo(() => {
    if (!input.from_atom_id || !input.to_atom_id) return undefined;
    return checkProposedRelationCycle(relations, input);
  }, [relations, input]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const nextErrors = validateRelationInput(input);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    await onSubmit(input);
    setInput(initial);
    setErrors({});
  }

  return (
    <form className="sg-planning-form" onSubmit={(event) => void submit(event)} aria-label="Create authority relation">
      <div className="sg-field">
        <Label htmlFor="relation-from">Source atom</Label>
        <select
          id="relation-from"
          className="sg-select"
          value={input.from_atom_id}
          aria-invalid={Boolean(errors.from_atom_id)}
          onChange={(event) => setInput((current) => ({ ...current, from_atom_id: event.target.value }))}
        >
          <option value="">Select an atom</option>
          {atomOptions.map((atom) => (
            <option key={atom.id} value={atom.id}>
              {atom.label}
            </option>
          ))}
        </select>
        {errors.from_atom_id ? <p className="sg-field-error">{errors.from_atom_id}</p> : null}
      </div>
      <div className="sg-field">
        <Label htmlFor="relation-to">Target atom</Label>
        <select
          id="relation-to"
          className="sg-select"
          value={input.to_atom_id}
          aria-invalid={Boolean(errors.to_atom_id)}
          onChange={(event) => setInput((current) => ({ ...current, to_atom_id: event.target.value }))}
        >
          <option value="">Select an atom</option>
          {atomOptions.map((atom) => (
            <option key={atom.id} value={atom.id}>
              {atom.label}
            </option>
          ))}
        </select>
        {errors.to_atom_id ? <p className="sg-field-error">{errors.to_atom_id}</p> : null}
      </div>
      <div className="sg-field">
        <Label htmlFor="relation-type">Relation type</Label>
        <select
          id="relation-type"
          className="sg-select"
          value={input.relation_type}
          onChange={(event) => setInput((current) => ({ ...current, relation_type: event.target.value as RelationInput["relation_type"] }))}
        >
          {RELATION_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
        {errors.relation_type ? <p className="sg-field-error">{errors.relation_type}</p> : null}
      </div>
      <div className="sg-field">
        <Label htmlFor="relation-rationale">Rationale</Label>
        <Textarea
          id="relation-rationale"
          value={input.rationale ?? ""}
          errorId={errors.rationale ? "relation-rationale-error" : undefined}
          onChange={(event) => setInput((current) => ({ ...current, rationale: event.target.value }))}
        />
        {errors.rationale ? (
          <p id="relation-rationale-error" className="sg-field-error">
            {errors.rationale}
          </p>
        ) : null}
      </div>
      <div className="sg-field">
        <Label htmlFor="relation-confidence">Confidence (0–1)</Label>
        <Input
          id="relation-confidence"
          type="number"
          min={0}
          max={1}
          step={0.05}
          value={input.confidence ?? ""}
          errorId={errors.confidence ? "relation-confidence-error" : undefined}
          onChange={(event) => setInput((current) => ({ ...current, confidence: event.target.value === "" ? undefined : Number(event.target.value) }))}
        />
        {errors.confidence ? (
          <p id="relation-confidence-error" className="sg-field-error">
            {errors.confidence}
          </p>
        ) : null}
      </div>
      <CycleAdvisory result={cycleResult} />
      <div className="sg-graph-command-group">
        <Button type="submit" loading={pending} disabled={pending}>
          Create relation
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
