type SafeKeyPart = string | number | boolean | null | undefined;

function safe(part: SafeKeyPart) {
  if (typeof part === "string" && /(bearer|token|signed|cursor|email)/i.test(part)) {
    throw new Error("unsafe query key part");
  }
  return part;
}

export const queryKeys = {
  health: () => ["health"] as const,
  project: (projectId: string) => ["project", safe(projectId)] as const,
  workspace: (projectId: string) => ["project", safe(projectId), "workspace"] as const,
  sources: (projectId: string) => ["project", safe(projectId), "sources"] as const,
  sourceWorkspace: (projectId: string) => ["project", safe(projectId), "source-workspace"] as const,
  sourceDocuments: (projectId: string, pageIndex: number) => ["project", safe(projectId), "source-documents", safe(pageIndex)] as const,
  sourceDocument: (documentId: string) => ["source-document", safe(documentId)] as const,
  sourceProvenance: (documentId: string) => ["source-document", safe(documentId), "provenance"] as const,
  sourceAtoms: (documentId: string, pageIndex: number) => ["source-document", safe(documentId), "atoms", safe(pageIndex)] as const,
  research: (projectId: string) => ["project", safe(projectId), "research"] as const,
  researchWorkspace: (projectId: string) => ["project", safe(projectId), "research-workspace"] as const,
  researchGapMatrix: (projectId: string) => ["project", safe(projectId), "research-gap-matrix"] as const,
  researchTasks: (projectId: string, pageIndex: number) => ["project", safe(projectId), "research-tasks", safe(pageIndex)] as const,
  researchTask: (taskId: string) => ["research-task", safe(taskId)] as const,
  planning: (projectId: string) => ["project", safe(projectId), "planning"] as const,
  graphRelations: (projectId: string) => ["project", safe(projectId), "graph-relations"] as const,
  graphPlan: (planId: string) => ["plan", safe(planId), "graph"] as const,
  planList: (projectId: string) => ["project", safe(projectId), "plans"] as const,
  readiness: (projectId: string) => ["project", safe(projectId), "readiness"] as const,
  operations: (projectId: string) => ["project", safe(projectId), "operations"] as const,
  exports: (projectId: string) => ["project", safe(projectId), "exports"] as const,
  execution: (projectId: string) => ["project", safe(projectId), "execution"] as const,
  routing: (projectId: string) => ["project", safe(projectId), "routing"] as const,
  operation: (operationId: string) => ["operation", safe(operationId)] as const,
  handoffWorkspace: (projectId: string) => ["project", safe(projectId), "handoff-workspace"] as const,
  bindings: (projectId: string) => ["project", safe(projectId), "bindings"] as const,
  exportDetail: (exportId: string) => ["export", safe(exportId)] as const,
  executionRunList: (projectId: string) => ["project", safe(projectId), "execution-runs"] as const,
  executionRunDetail: (runId: string) => ["execution-run", safe(runId)] as const,
  routingPolicy: (projectId: string) => ["project", safe(projectId), "routing-policy"] as const,
  providers: (projectId: string) => ["project", safe(projectId), "providers"] as const,
  renderers: (projectId: string) => ["project", safe(projectId), "renderers"] as const,
  routeDecisionDetail: (decisionId: string) => ["route-decision", safe(decisionId)] as const,
};
