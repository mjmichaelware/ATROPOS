"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import type { Binding } from "@/lib/handoff/schemas";
import { BindingForm } from "./binding-form";

export function BindingList({ projectId, bindings, onChanged }: { projectId: string; bindings: Binding[]; onChanged: () => void }) {
  const [editing, setEditing] = useState<Binding | "new" | undefined>();

  if (editing) {
    return <BindingForm projectId={projectId} existing={editing === "new" ? undefined : editing} onDone={() => { setEditing(undefined); onChanged(); }} />;
  }

  return (
    <div className="sg-planning-form">
      <p className="sg-muted">
        A binding connects this project to a real external system — where an execution run actually gets carried out. Nothing here can start a run on its own; a binding just has to exist and be
        enabled before the Runs tab can target it.
      </p>
      {bindings.length === 0 ? <p className="sg-muted">No integration bindings are configured yet. Add one below before you can start an execution run.</p> : null}
      <ul className="sg-plan-history" aria-label="Integration bindings">
        {bindings.map((binding) => (
          <li key={binding.id}>
            <Card>
              <div className="sg-page-heading">
                <div>
                  <strong>{binding.system_name ?? "Unnamed system"}</strong>
                  <p className="sg-micro-label">{binding.binding_type ?? "unknown type"}</p>
                </div>
                <StatusBadge tone={binding.enabled ? "success" : "neutral"} label={binding.enabled ? "Enabled" : "Disabled"} />
              </div>
              <Button type="button" variant="secondary" onClick={() => setEditing(binding)}>
                Edit
              </Button>
            </Card>
          </li>
        ))}
      </ul>
      <Button type="button" onClick={() => setEditing("new")}>
        Add binding
      </Button>
    </div>
  );
}
