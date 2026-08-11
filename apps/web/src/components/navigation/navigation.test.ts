import { describe, expect, it } from "vitest";
import {
  activeProjectSection,
  globalRoutes,
  isActiveGlobalRoute,
  projectDocumentRoute,
  projectExecutionRoute,
  projectGraphRoute,
  projectHandoffRoute,
  projectResearchRoute,
  projectRoute,
  projectRoutingRoute,
  projectSections,
  projectSourcesRoute,
  projectTaskRoute,
} from "./routes";
import { projectIdFromPathname } from "./route-utils";

describe("central route registry", () => {
  it("builds every project-relative route from a project id, never a literal path", () => {
    expect(projectRoute("proj-1")).toBe("/projects/proj-1");
    expect(projectSourcesRoute("proj-1")).toBe("/developer/specgraph/proj-1/sources");
    expect(projectDocumentRoute("proj-1", "doc-1")).toBe("/developer/specgraph/proj-1/sources/doc-1");
    expect(projectResearchRoute("proj-1")).toBe("/developer/specgraph/proj-1/research");
    expect(projectTaskRoute("proj-1", "task-1")).toBe("/developer/specgraph/proj-1/research/tasks/task-1");
    expect(projectGraphRoute("proj-1")).toBe("/developer/specgraph/proj-1/graph");
    expect(projectHandoffRoute("proj-1")).toBe("/developer/specgraph/proj-1/handoff");
    expect(projectExecutionRoute("proj-1", "run-1")).toBe("/developer/specgraph/proj-1/executions/run-1");
    expect(projectRoutingRoute("proj-1")).toBe("/developer/specgraph/proj-1/routing");
  });

  it("exposes every navigable section with a working project-aware builder", () => {
    expect(projectSections.length).toBeGreaterThan(0);
    for (const section of projectSections) {
      const built = section.build("proj-9");
      expect(built.startsWith("/projects/proj-9")).toBe(true);
    }
  });

  it("resolves the active section from a pathname without hardcoding project ids", () => {
    expect(activeProjectSection("/developer/specgraph/proj-1/sources/doc-1", "proj-1")?.id).toBe("sources");
    expect(activeProjectSection("/developer/specgraph/proj-1/research", "proj-1")?.id).toBe("research");
    expect(activeProjectSection("/developer/specgraph/proj-1/graph", "proj-1")?.id).toBe("graph");
    expect(activeProjectSection("/developer/specgraph/proj-1/handoff", "proj-1")?.id).toBe("handoff");
    expect(activeProjectSection("/developer/specgraph/proj-1/executions/run-1", "proj-1")?.id).toBe("handoff");
    expect(activeProjectSection("/developer/specgraph/proj-1/routing", "proj-1")?.id).toBe("routing");
    expect(activeProjectSection("/developer/specgraph/proj-1", "proj-1")?.id).toBe("overview");
  });

  it("does not cross-match a different project's routes", () => {
    expect(activeProjectSection("/developer/specgraph/proj-2/sources", "proj-1")).toBeUndefined();
  });

  it("marks the global projects route active for any project detail page but not for new-project", () => {
    expect(isActiveGlobalRoute("/projects", globalRoutes.projects)).toBe(true);
    expect(isActiveGlobalRoute("/projects/proj-1", globalRoutes.projects)).toBe(true);
    expect(isActiveGlobalRoute("/developer/specgraph/new", globalRoutes.projects)).toBe(false);
  });

  it("extracts a project id from a pathname only for active-state matching", () => {
    expect(projectIdFromPathname("/developer/specgraph/proj-1/sources")).toBe("proj-1");
    expect(projectIdFromPathname("/developer/specgraph/new")).toBeUndefined();
    expect(projectIdFromPathname("/projects")).toBeUndefined();
  });
});
