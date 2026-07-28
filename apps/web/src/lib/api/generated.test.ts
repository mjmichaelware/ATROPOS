import type { operations, paths } from "./generated";
import { describe, expect, it } from "vitest";

describe("generated OpenAPI types", () => {
  it("contains critical Group 10 operation families", () => {
    type RequiredOperations =
      | operations["getCurrentUser"]
      | operations["listProjects"]
      | operations["getProjectWorkspace"]
      | operations["createSourceUploadIntent"]
      | operations["getDocumentProvenance"]
      | operations["listProjectResearchTasks"]
      | operations["synthesizeProjectPlan"]
      | operations["downloadExportArtifacts"]
      | operations["startExecutionRun"]
      | operations["createProjectRouteDecision"]
      | operations["getOperation"]
      | operations["getHealthReady"];

    const operation: RequiredOperations | null = null;
    const path: keyof paths = "/health/ready";
    expect(operation).toBeNull();
    expect(path).toBe("/health/ready");
  });
});
