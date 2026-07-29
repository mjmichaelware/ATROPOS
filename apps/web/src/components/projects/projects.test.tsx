import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { ProjectCommandCenter } from "./project-command-center";
import { ProjectCreateForm } from "./project-create-form";
import { ProjectDirectory } from "./project-directory";

const listProjects = vi.fn();
const createProject = vi.fn();
const getProject = vi.fn();
const getWorkspace = vi.fn();
const getReadiness = vi.fn();
const getOperations = vi.fn();
const push = vi.fn();

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({}),
  listProjects: (...args: unknown[]) => listProjects(...args),
  createProject: (...args: unknown[]) => createProject(...args),
  getProject: (...args: unknown[]) => getProject(...args),
  getWorkspace: (...args: unknown[]) => getWorkspace(...args),
  getReadiness: (...args: unknown[]) => getReadiness(...args),
  getOperations: (...args: unknown[]) => getOperations(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

function renderProject(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.stubGlobal("localStorage", { setItem: vi.fn(), getItem: vi.fn() });
  listProjects.mockResolvedValue({
    body: { items: [{ id: "11111111-1111-1111-1111-111111111111", slug: "alpha", name: "Alpha", created_at: "2026-01-01T00:00:00Z" }] },
    pagination: { hasMore: true, nextCursor: "cursor-1" },
  });
  createProject.mockResolvedValue({ body: { id: "22222222-2222-2222-2222-222222222222", slug: "new", name: "New" } });
  getProject.mockResolvedValue({ body: { id: "11111111-1111-1111-1111-111111111111", slug: "alpha", name: "Alpha" } });
  // Shape matches ProjectWorkspaceService.get() exactly (workspace.py):
  // counts/latest/readiness are nested objects, never flat *_count keys or
  // a bare readiness string - a prior version of this fixture used the
  // wrong flat shape and matched a real bug in the components under test.
  getWorkspace.mockResolvedValue({
    body: {
      counts: { documents: 1, atoms: 2, dimensions: 4, resolved_dimensions: 3, not_applicable_dimensions: 0, plans: 1, verified_plans: 0, exports: 0, verified_exports: 0, execution_runs: 0, verified_execution_runs: 0 },
      latest: { document: { id: "doc-1", title: "Doc" }, plan: null, export: null, execution_run: null, route_decision: null },
      readiness: { status: "READY_TO_PLAN", next_action: "SYNTHESIZE_PLAN", stages: [{ name: "SOURCE", status: "COMPLETE", count: 1 }, { name: "ATOMS", status: "COMPLETE", count: 2 }, { name: "RESEARCH", status: "COMPLETE", open_dimensions: 0 }, { name: "PLANNING", status: "READY", count: 1 }, { name: "INTEGRATION", status: "BLOCKED", count: 0 }, { name: "EXPORT", status: "BLOCKED", count: 0 }, { name: "EXECUTION", status: "BLOCKED", count: 0 }] },
    },
  });
  getReadiness.mockResolvedValue({ body: { readiness: { status: "READY_TO_PLAN", next_action: "SYNTHESIZE_PLAN", stages: [{ name: "SOURCE", status: "COMPLETE", count: 1 }, { name: "ATOMS", status: "COMPLETE", count: 2 }, { name: "RESEARCH", status: "COMPLETE", open_dimensions: 0 }, { name: "PLANNING", status: "READY", count: 1 }, { name: "INTEGRATION", status: "BLOCKED", count: 0 }, { name: "EXPORT", status: "BLOCKED", count: 0 }, { name: "EXECUTION", status: "BLOCKED", count: 0 }] } } });
  getOperations.mockResolvedValue({ body: { items: [{ id: "op", state: "QUEUED", operation_type: "extract_document_atoms" }] } });
});

describe("projects experience", () => {
  it("renders paginated project directory and next navigation", async () => {
    renderProject(<ProjectDirectory />);
    expect(await screen.findByText("Alpha")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(listProjects).toHaveBeenCalled();
  });

  it("creates a project from just a name, deriving the slug automatically", async () => {
    renderProject(<ProjectCreateForm />);
    expect(screen.queryByLabelText("Slug")).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Name"), "New Project");
    await userEvent.click(screen.getByRole("button", { name: "Create project" }));
    await waitFor(() => {
      expect(push).toHaveBeenCalledWith("/developer/specgraph/22222222-2222-2222-2222-222222222222");
    });
    expect(createProject).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ name: "New Project", slug: "new-project" }),
    );
  });

  it("renders command center from authoritative API data", async () => {
    renderProject(<ProjectCommandCenter projectId="11111111-1111-1111-1111-111111111111" />);
    expect(await screen.findByRole("heading", { name: "Alpha" })).toBeInTheDocument();
    expect(screen.getByText("Ready to plan")).toBeInTheDocument();
    expect(screen.getByText("extract_document_atoms")).toBeInTheDocument();
    // Regression: the project's own UUID (needed for SPECGRAPH_PROJECT_ID in
    // external scripts) was never surfaced anywhere in the app.
    expect(screen.getByText("11111111-1111-1111-1111-111111111111")).toBeInTheDocument();
    // Regression: readiness/counts/latest all nest their real data one level
    // deeper than the components used to read (workspace.counts.*,
    // workspace.latest.*, and readiness as an object, not a plain string) -
    // every one of these previously rendered as 0/"None yet"/"Unknown
    // readiness" against this exact fixture shape.
    expect(screen.getByText("Extraction")).toBeInTheDocument();
    expect(screen.getAllByText("COMPLETE").length).toBeGreaterThan(0);
    expect(screen.getByText("Doc")).toBeInTheDocument();
  });

  it("has no serious or critical axe violations on the command center", async () => {
    const { container } = renderProject(<ProjectCommandCenter projectId="11111111-1111-1111-1111-111111111111" />);
    await screen.findByRole("heading", { name: "Alpha" });
    await expectNoSeriousAxeViolations(container);
  });
});
