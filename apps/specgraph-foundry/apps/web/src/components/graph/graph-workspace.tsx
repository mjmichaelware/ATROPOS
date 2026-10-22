"use client";

import { ReactFlowProvider } from "@xyflow/react";
import type { Route } from "next";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { StatusBadge } from "@/components/ui/status-badge";
import { VisuallyHidden } from "@/components/ui/visually-hidden";
import { BindingPanel } from "@/components/planning/binding-panel";
import { PlanningRail, type PlanningTab } from "@/components/planning/planning-rail";
import { useOnlineStatus } from "@/lib/graph/connectivity";
import { createBrowserGraphLayoutWorker, GraphLayoutClient, LayoutSupersededError } from "@/lib/graph/layout-client";
import { buildLayoutRequest } from "@/lib/graph/layout-normalize";
import { loadLayoutPreference, saveLayoutPreference } from "@/lib/graph/layout-preferences";
import { usePrefersReducedMotion } from "@/lib/graph/motion";
import { useGraphProject, usePlanGraph, usePlanningWorkspace, useAuthorityRelations } from "@/lib/graph/queries";
import { createEmptyLayoutState, type GraphMode, type LayoutAlgorithm, type LayoutPosition } from "@/lib/graph/schemas";
import { availableCategories, availableStatuses, emptyGraphFilterState, filterGraphContent, neighborhood, type GraphFilterState } from "@/lib/graph/search";
import { mergeLayoutPositions, relationsToRendererContent, semanticGraphToRendererContent, type RendererGraphContent } from "@/lib/graph/transform";
import { parseGraphUrlState, serializeGraphUrlState } from "@/lib/graph/url-state";
import { graphSizeTier } from "@/lib/graph/zoom";
import { bindingByNodeId, isServerReadyNode } from "@/lib/planning/bindings";
import { useCreateRelationMutation, useSynthesizePlanMutation, useVerifyPlanMutation } from "@/lib/planning/mutations";
import { usePlanList } from "@/lib/planning/queries";
import type { PlanBinding, RelationInput } from "@/lib/planning/schemas";
import { GraphAccessibleList } from "./graph-accessible-list";
import { GraphCanvas, type GraphCanvasHandle } from "./graph-canvas";
import { GraphCommandDock } from "./graph-command-dock";
import { GraphEmptyState } from "./graph-empty-state";
import { GraphErrorState } from "./graph-error-state";
import { GraphHeader } from "./graph-header";
import { GraphInspector } from "./graph-inspector";
import { GraphLargeModeNotice } from "./graph-large-mode-notice";
import { GraphLayoutControl } from "./graph-layout-control";
import { GraphLoadingState } from "./graph-loading-state";
import { GraphOfflineState } from "./graph-offline-state";
import { GraphSearchPanel } from "./graph-search-panel";

function contentKey(content: RendererGraphContent): string {
  return `${content.nodes.map((node) => node.id).sort().join(",")}|${content.edges.map((edge) => edge.id).sort().join(",")}`;
}

export function GraphWorkspace({ projectId }: { projectId: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const online = useOnlineStatus();
  const reducedMotion = usePrefersReducedMotion();
  const canvasRef = useRef<GraphCanvasHandle>(null);
  const layoutClientRef = useRef<GraphLayoutClient | null>(null);

  const urlState = useMemo(() => parseGraphUrlState(searchParams), [searchParams]);

  const updateUrlState = useCallback(
    (partial: Partial<typeof urlState>) => {
      const next = { ...urlState, ...partial };
      const params = serializeGraphUrlState(next);
      const query = params.toString();
      router.replace((query ? `${pathname}?${query}` : pathname) as Route, { scroll: false });
    },
    [router, pathname, urlState],
  );

  const project = useGraphProject(projectId);
  const workspace = usePlanningWorkspace(projectId);
  const planList = usePlanList(projectId);
  const latestPlanId = (workspace.data?.body.latest_plan?.id as string | undefined) ?? undefined;
  const explicitPlanId = urlState.plan;
  const effectivePlanId = explicitPlanId ?? latestPlanId;
  const plan = usePlanGraph(effectivePlanId);
  const relations = useAuthorityRelations(projectId, urlState.mode === "authority");

  const [planningTab, setPlanningTab] = useState<PlanningTab>("overview");
  const [announcement, setAnnouncement] = useState<string | undefined>();
  const [mutationError, setMutationError] = useState<string | undefined>();

  const createRelation = useCreateRelationMutation(projectId);
  const [progressMessage, setProgressMessage] = useState<string | undefined>();
  const synthesize = useSynthesizePlanMutation(projectId, setProgressMessage);
  const verify = useVerifyPlanMutation(projectId, effectivePlanId, setProgressMessage);

  const [layoutVersion, setLayoutVersion] = useState(0);
  const [layoutError, setLayoutError] = useState<string | undefined>();

  const graphId = urlState.mode === "authority" ? (plan.data?.body.authority_graph?.id ?? "relations") : (plan.data?.body.execution_graph?.id ?? "none");

  const layoutState = useMemo(() => {
    if (typeof window === "undefined") return createEmptyLayoutState(urlState.algorithm);
    return loadLayoutPreference(window.localStorage, projectId, graphId, urlState.algorithm);
    // layoutVersion is a manual invalidation signal for the localStorage read below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, graphId, urlState.algorithm, layoutVersion]);

  const layoutStateRef = useRef(layoutState);
  useEffect(() => {
    layoutStateRef.current = layoutState;
  }, [layoutState]);

  const persistLayout = useCallback(
    (next: ReturnType<typeof createEmptyLayoutState>) => {
      if (typeof window !== "undefined") saveLayoutPreference(window.localStorage, projectId, graphId, next);
      setLayoutVersion((value) => value + 1);
    },
    [projectId, graphId],
  );

  useEffect(() => {
    layoutClientRef.current = new GraphLayoutClient(createBrowserGraphLayoutWorker);
    return () => {
      layoutClientRef.current?.terminate();
      layoutClientRef.current = null;
    };
  }, []);

  const content: RendererGraphContent = useMemo(() => {
    if (urlState.mode === "execution") {
      return semanticGraphToRendererContent(plan.data?.body.execution_graph);
    }
    const authorityGraph = plan.data?.body.authority_graph;
    if (authorityGraph && authorityGraph.nodes?.length) {
      return semanticGraphToRendererContent(authorityGraph);
    }
    return relationsToRendererContent(relations.data?.body.items ?? []);
  }, [urlState.mode, plan.data, relations.data]);

  const filters: GraphFilterState = useMemo(
    () => ({ ...emptyGraphFilterState(), query: urlState.query, category: (urlState.category as GraphFilterState["category"]) ?? "all", status: urlState.status }),
    [urlState.query, urlState.category, urlState.status],
  );

  const filteredContent = useMemo(() => filterGraphContent(content, filters), [content, filters]);
  const size = graphSizeTier(filteredContent.nodes.length);

  const layoutInputContent = useMemo(() => {
    if (urlState.algorithm === "focus" && urlState.selected) {
      return neighborhood(urlState.selected, filteredContent);
    }
    return filteredContent;
  }, [urlState.algorithm, urlState.selected, filteredContent]);

  const layoutKey = contentKey(layoutInputContent);

  useEffect(() => {
    if (urlState.algorithm === "freeform") return;
    if (urlState.algorithm === "focus" && !urlState.selected) return;
    const client = layoutClientRef.current;
    if (!client) return;
    let cancelled = false;
    const request = buildLayoutRequest(layoutInputContent, urlState.algorithm, client.nextGeneration());
    client
      .requestLayout(request)
      .then((positions) => {
        if (cancelled) return;
        setLayoutError(undefined);
        const current = layoutStateRef.current;
        persistLayout({ ...current, algorithm: urlState.algorithm, nodePositions: { ...current.nodePositions, ...positions }, generatedAt: new Date().toISOString() });
      })
      .catch((error: unknown) => {
        if (cancelled || error instanceof LayoutSupersededError) return;
        setLayoutError(error instanceof Error ? error.message : "Layout failed");
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layoutKey, urlState.algorithm, urlState.selected, projectId, graphId]);

  const positionedNodes = useMemo(() => mergeLayoutPositions(filteredContent.nodes, layoutState), [filteredContent.nodes, layoutState]);

  const handleNodeMoved = useCallback(
    (id: string, position: LayoutPosition) => {
      const current = layoutStateRef.current;
      persistLayout({ ...current, algorithm: "freeform" as LayoutAlgorithm, nodePositions: { ...current.nodePositions, [id]: position }, generatedAt: new Date().toISOString() });
    },
    [persistLayout],
  );

  const atomOptions = useMemo(
    () =>
      content.nodes
        .filter((node) => node.data.category === "atom" && node.data.atomId)
        .map((node) => ({ id: node.data.atomId as string, label: node.data.label })),
    [content.nodes],
  );

  const loadedNodeIds = useMemo(() => new Set(content.nodes.map((node) => node.id)), [content.nodes]);

  const handleCreateRelation = useCallback(
    async (input: RelationInput) => {
      setMutationError(undefined);
      try {
        const result = await createRelation.mutateAsync(input);
        setAnnouncement(`Relation created: ${result.body.relation_type ?? input.relation_type}. Resynthesizing the plan may be needed to reflect this in the execution graph.`);
        updateUrlState({ mode: "authority", selected: result.body.id });
      } catch (error) {
        setMutationError(error instanceof Error ? error.message : "Relation creation failed. The existing graph is unchanged.");
      }
    },
    [createRelation, updateUrlState],
  );

  const handleSynthesize = useCallback(
    async (allowOpenResearch: boolean) => {
      setMutationError(undefined);
      try {
        const result = await synthesize.mutateAsync(allowOpenResearch);
        const resultRecord = result.body.operation.result as Record<string, unknown> | undefined;
        const returnedPlanId = typeof resultRecord?.plan_id === "string" ? resultRecord.plan_id : undefined;
        const returnedStatus = typeof resultRecord?.status === "string" ? resultRecord.status : result.body.operation.state;
        setAnnouncement(`Plan synthesis ${result.body.operation.state}. Resulting plan status: ${returnedStatus}.`);
        if (returnedPlanId) {
          updateUrlState({ plan: returnedPlanId, mode: "execution", selected: undefined });
        }
        setPlanningTab("verify");
      } catch (error) {
        setMutationError(error instanceof Error ? error.message : "Plan synthesis failed. The existing graph is unchanged.");
      }
    },
    [synthesize, updateUrlState],
  );

  const handleVerify = useCallback(async () => {
    setMutationError(undefined);
    try {
      const result = await verify.mutateAsync();
      const resultRecord = result.body.operation.result as Record<string, unknown> | undefined;
      const returnedStatus = typeof resultRecord?.status === "string" ? resultRecord.status : result.body.operation.state;
      setAnnouncement(`Plan verification ${result.body.operation.state}. Resulting plan status: ${returnedStatus}.`);
    } catch (error) {
      setMutationError(error instanceof Error ? error.message : "Plan verification failed. The existing graph is unchanged.");
    }
  }, [verify]);

  const handleFocusNode = useCallback(
    (nodeId: string) => {
      updateUrlState({ selected: nodeId, view: "canvas" });
    },
    [updateUrlState],
  );

  const isLoading = project.isLoading || workspace.isLoading || (Boolean(effectivePlanId) && plan.isLoading);
  const isError = project.isError || workspace.isError;

  const refetchAll = useCallback(() => {
    void Promise.all([project.refetch(), workspace.refetch(), plan.refetch(), relations.refetch(), planList.refetch()]);
  }, [project, workspace, plan, relations, planList]);

  if (!online) {
    return <GraphOfflineState />;
  }
  if (isLoading) {
    return <GraphLoadingState />;
  }
  if (isError) {
    return <GraphErrorState onRetry={refetchAll} />;
  }

  const projectName = String(project.data?.body.name ?? "Project");
  const selectedNode = positionedNodes.find((node) => node.id === urlState.selected);
  const selectedEdge = filteredContent.edges.find((edge) => edge.id === urlState.selected);
  const bindings = (plan.data?.body.bindings ?? []) as PlanBinding[];
  const readyNodes = plan.data?.body.ready_nodes;
  const selectedBinding = selectedNode && urlState.mode === "execution" ? bindingByNodeId(bindings, selectedNode.id) : undefined;
  const selectedIsServerReady = selectedNode && urlState.mode === "execution" ? isServerReadyNode(selectedNode.id, readyNodes) : false;

  const nodeExtra =
    urlState.mode === "execution" && selectedNode ? (
      <div className="sg-graph-inspector-extra">
        {selectedIsServerReady ? <StatusBadge tone="success" label="Server-ready" /> : null}
        <BindingPanel binding={selectedBinding} />
      </div>
    ) : null;

  return (
    <section className="sg-graph-workspace" aria-label="Graph foundation">
      {explicitPlanId && plan.isError ? (
        <Alert tone="danger" title="Selected plan unavailable">
          <p>The plan referenced by this link could not be found or is not accessible. It has not been silently replaced with another plan.</p>
        </Alert>
      ) : null}
      {mutationError ? (
        <Alert tone="danger" title="Action failed">
          <p>{mutationError}</p>
        </Alert>
      ) : null}
      {(synthesize.isPending || verify.isPending) && progressMessage ? (
        <p role="status" aria-live="polite" className="sg-micro-label">
          {progressMessage}
        </p>
      ) : (
        <VisuallyHidden role="status">{announcement}</VisuallyHidden>
      )}
      <GraphHeader
        projectName={projectName}
        mode={urlState.mode}
        onModeChange={(mode: GraphMode) => updateUrlState({ mode, selected: undefined })}
        nodeCount={filteredContent.nodes.length}
        edgeCount={filteredContent.edges.length}
        size={size}
        isFetching={workspace.isFetching || plan.isFetching}
        onRefresh={refetchAll}
      />
      <PlanningRail
        projectId={projectId}
        tab={planningTab}
        onTabChange={setPlanningTab}
        workspace={workspace.data?.body}
        atomOptions={atomOptions}
        relations={relations.data?.body.items ?? []}
        createRelationPending={createRelation.isPending}
        onCreateRelation={handleCreateRelation}
        plans={planList.data?.body.items ?? []}
        selectedPlanId={effectivePlanId}
        onSelectPlan={(planId) => updateUrlState({ plan: planId })}
        synthesizePending={synthesize.isPending}
        onSynthesize={handleSynthesize}
        selectedPlan={plan.data?.body}
        verifyPending={verify.isPending}
        onVerify={handleVerify}
        findingFilter={urlState.findingFilter}
        onFindingFilterChange={(findingFilter) => updateUrlState({ findingFilter })}
        loadedNodeIds={loadedNodeIds}
        onFocusNode={handleFocusNode}
      />
      {content.nodes.length === 0 ? (
        <GraphEmptyState mode={urlState.mode} />
      ) : (
        <div className="sg-graph-layout-shell" data-view={urlState.view}>
          <div className="sg-graph-controls">
            <GraphLayoutControl
              value={urlState.algorithm}
              onChange={(algorithm) => updateUrlState({ algorithm })}
              hasSelection={Boolean(urlState.selected)}
            />
            <GraphSearchPanel
              filters={filters}
              categories={availableCategories(content)}
              statuses={availableStatuses(content)}
              resultCount={filteredContent.nodes.length}
              onChange={(next) => updateUrlState({ query: next.query, category: next.category, status: next.status })}
              onClear={() => updateUrlState({ query: "", category: "all", status: "all" })}
            />
          </div>
          <GraphLargeModeNotice size={size} nodeCount={filteredContent.nodes.length} />
          {layoutError ? <p role="alert">Layout could not complete: {layoutError}. The previous graph is preserved.</p> : null}
          <GraphCommandDock
            view={urlState.view}
            onViewChange={(view) => updateUrlState({ view })}
            hasSelection={Boolean(urlState.selected)}
            onFitGraph={() => canvasRef.current?.fitGraph()}
            onFitSelection={() => canvasRef.current?.fitSelection()}
            onZoomIn={() => canvasRef.current?.zoomIn()}
            onZoomOut={() => canvasRef.current?.zoomOut()}
            onResetView={() => canvasRef.current?.resetView()}
          />
          <div className="sg-graph-main">
            {urlState.view === "canvas" ? (
              <ReactFlowProvider>
                <GraphCanvas
                  ref={canvasRef}
                  nodes={positionedNodes}
                  edges={filteredContent.edges}
                  selectedId={urlState.selected}
                  reducedMotion={reducedMotion}
                  onSelectNode={(id) => updateUrlState({ selected: id })}
                  onSelectEdge={(id) => updateUrlState({ selected: id })}
                  onNodeMoved={handleNodeMoved}
                  onZoomChange={() => {}}
                />
              </ReactFlowProvider>
            ) : (
              <GraphAccessibleList content={filteredContent} selectedId={urlState.selected} onSelect={(id) => updateUrlState({ selected: id })} />
            )}
            <GraphInspector
              selectedNode={selectedNode}
              selectedEdge={selectedEdge}
              content={filteredContent}
              onClose={() => updateUrlState({ selected: undefined })}
              nodeExtra={nodeExtra}
            />
          </div>
        </div>
      )}
    </section>
  );
}
