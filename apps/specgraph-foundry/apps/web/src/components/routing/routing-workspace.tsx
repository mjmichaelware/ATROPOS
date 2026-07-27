"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Tabs } from "@/components/ui/tabs";
import { useOnlineStatus } from "@/lib/graph/connectivity";
import { useHandoffWorkspace } from "@/lib/handoff/queries";
import { useProjectProviders, useProjectRenderers } from "@/lib/routing/queries";
import { PaidUnlockPanel } from "./paid-unlock-panel";
import { ProviderList } from "./provider-list";
import { RendererList } from "./renderer-list";
import { RouteDecisionPanel } from "./route-decision-panel";
import { RoutingErrorState } from "./routing-error-state";
import { RoutingLoadingState } from "./routing-loading-state";
import { RoutingOfflineState } from "./routing-offline-state";
import { RoutingPolicyPanel } from "./routing-policy-panel";

type RoutingTab = "policy" | "providers" | "renderers" | "unlocks" | "decisions";

export function RoutingWorkspace({ projectId }: { projectId: string }) {
  const online = useOnlineStatus();
  const [tab, setTab] = useState<RoutingTab>("policy");
  const workspace = useHandoffWorkspace(projectId);
  const providers = useProjectProviders(projectId);
  const renderers = useProjectRenderers(projectId);

  if (!online) {
    return <RoutingOfflineState />;
  }
  if (workspace.isLoading) {
    return <RoutingLoadingState />;
  }
  if (workspace.isError) {
    return <RoutingErrorState onRetry={() => void workspace.refetch()} />;
  }

  const providerItems = providers.data?.body.items ?? [];
  const rendererItems = renderers.data?.body.items ?? [];

  return (
    <section className="sg-graph-workspace" aria-label="Routing workspace">
      <header className="sg-source-hero sg-graph-hero">
        <p className="sg-micro-label">Routing</p>
        <h1>{String(workspace.data?.body.project?.name ?? "Project")}</h1>
        <p>Policy, providers, renderers, unlocks, and decisions use only real server-authoritative routing state.</p>
        <Button type="button" variant="secondary" onClick={() => { void workspace.refetch(); void providers.refetch(); void renderers.refetch(); }}>
          Refresh
        </Button>
      </header>
      <Tabs
        label="Routing workspace views"
        value={tab}
        onChange={setTab}
        tabs={[
          { value: "policy", label: "Policy", panel: <RoutingPolicyPanel projectId={projectId} /> },
          { value: "providers", label: "Providers", panel: <ProviderList projectId={projectId} providers={providerItems} onChanged={() => void providers.refetch()} /> },
          { value: "renderers", label: "Renderers", panel: <RendererList projectId={projectId} renderers={rendererItems} onChanged={() => void renderers.refetch()} /> },
          { value: "unlocks", label: "Unlocks", panel: <PaidUnlockPanel projectId={projectId} providers={providerItems} /> },
          { value: "decisions", label: "Decisions", panel: <RouteDecisionPanel projectId={projectId} /> },
        ]}
      />
    </section>
  );
}
