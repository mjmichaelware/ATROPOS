"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/status-badge";
import { createProjectApiClient } from "@/lib/projects/api";
import { useSelectRendererMutation } from "@/lib/routing/mutations";
import type { Renderer } from "@/lib/routing/schemas";
import { RendererForm } from "./renderer-form";

export function RendererList({ projectId, renderers, onChanged }: { projectId: string; renderers: Renderer[]; onChanged: () => void }) {
  const [editing, setEditing] = useState<Renderer | "new" | undefined>();
  const [territory, setTerritory] = useState("");
  const selectMutation = useSelectRendererMutation(projectId);

  if (editing) {
    return <RendererForm projectId={projectId} existing={editing === "new" ? undefined : editing} onDone={() => { setEditing(undefined); onChanged(); }} />;
  }

  return (
    <div className="sg-planning-form">
      <p className="sg-muted">
        A renderer turns a verified plan into a specific output format — an export package, a report, a diagram — for one territory (a named scope, like a document type or a target system). Each
        territory picks one active renderer at a time; selecting a new one below for a territory replaces whichever was picked before.
      </p>
      {renderers.length === 0 ? (
        <p className="sg-muted">No renderers are configured yet. Add one below, then select it for a territory to make it the active one.</p>
      ) : null}
      <ul className="sg-plan-history" aria-label="Renderer configurations">
        {renderers.map((renderer) => (
          <li key={renderer.id}>
            <Card>
              <div className="sg-page-heading">
                <div>
                  <strong>{renderer.name ?? "Unnamed renderer"}</strong>
                  <p className="sg-micro-label">{renderer.renderer_type ?? "unknown type"}</p>
                </div>
                <StatusBadge tone={renderer.enabled ? "success" : "neutral"} label={renderer.enabled ? "Enabled" : "Disabled"} />
              </div>
              <Button type="button" variant="secondary" onClick={() => setEditing(renderer)}>
                Edit
              </Button>
            </Card>
          </li>
        ))}
      </ul>
      <Button type="button" onClick={() => setEditing("new")}>
        Add renderer
      </Button>
      <form
        className="sg-planning-form"
        aria-label="Select a renderer for a territory"
        onSubmit={(event) => {
          event.preventDefault();
          if (!territory.trim()) return;
          void selectMutation.mutateAsync({ input: { territory: territory.trim() }, idempotencyKey: createProjectApiClient().createIdempotencyKey() }).catch(() => {});
        }}
      >
        <Field id="renderer-territory" label="Territory" value={territory} onChange={(event) => setTerritory(event.target.value)} required maxLength={160} />
        <Button type="submit" loading={selectMutation.isPending} disabled={selectMutation.isPending}>
          Select renderer for territory
        </Button>
        {selectMutation.data ? (
          <p role="status">
            Selected renderer: <span className="sg-mono">{String(selectMutation.data.body.renderer.id ?? "unknown")}</span>
          </p>
        ) : null}
      </form>
    </div>
  );
}
