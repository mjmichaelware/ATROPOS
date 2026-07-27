import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { GraphWorkspace } from "./graph-workspace";

const getProject = vi.fn();
const getPlanningWorkspace = vi.fn();
const listProjectRelations = vi.fn();
const listProjectPlans = vi.fn();
const getPlan = vi.fn();
const createProjectRelation = vi.fn();
const synthesizePlan = vi.fn();
const verifyPlan = vi.fn();
const pollOperation = vi.fn();

const routerReplace = vi.fn();
let currentSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: routerReplace }),
  usePathname: () => "/projects/project-1/graph",
  useSearchParams: () => currentSearchParams,
}));

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({ createIdempotencyKey: () => "idempotency-key", pollOperation }),
  getProject: (...args: unknown[]) => getProject(...args),
}));

vi.mock("@/lib/graph/api", () => ({
  getPlanningWorkspace: (...args: unknown[]) => getPlanningWorkspace(...args),
  listProjectRelations: (...args: unknown[]) => listProjectRelations(...args),
  listProjectPlans: (...args: unknown[]) => listProjectPlans(...args),
  getPlan: (...args: unknown[]) => getPlan(...args),
}));

vi.mock("@/lib/planning/api", () => ({
  createProjectRelation: (...args: unknown[]) => createProjectRelation(...args),
  synthesizePlan: (...args: unknown[]) => synthesizePlan(...args),
  verifyPlan: (...args: unknown[]) => verifyPlan(...args),
}));

vi.mock("@/lib/graph/layout-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/graph/layout-client")>();
  return {
    ...actual,
    createBrowserGraphLayoutWorker: () => {
      const worker: import("@/lib/graph/layout-client").WorkerLike = {
        onmessage: null,
        onerror: null,
        postMessage: (request) => {
          queueMicrotask(() => worker.onmessage?.({ data: { generation: request.generation, ok: true, positions: {} } } as MessageEvent));
        },
        terminate: () => {},
      };
      return worker;
    },
  };
});

class FakeResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

function fakeMatchMedia(query: string) {
  return { matches: query.includes("min-width: 768px"), media: query, addEventListener: () => {}, removeEventListener: () => {} } as unknown as MediaQueryList;
}

function renderGraph(projectId = "project-1") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <GraphWorkspace projectId={projectId} />
    </QueryClientProvider>,
  );
}

const PLAN_DETAIL = {
  id: "plan-1",
  status: "VERIFIED",
  authority_graph: {
    id: "authority-graph-1",
    nodes: [
      { id: "node-a", node_type: "ATOM", title: "Atom A", status: "READY", payload: { atom_id: "atom-a" } },
      { id: "node-b", node_type: "ATOM", title: "Atom B", status: "READY", payload: { atom_id: "atom-b" } },
    ],
    edges: [],
  },
  execution_graph: {
    id: "execution-graph-1",
    nodes: [{ id: "exec-node-1", node_type: "CONTRACT", title: "Contract", status: "READY" }],
    edges: [],
  },
  ready_nodes: [{ id: "exec-node-1" }],
  bindings: [{ id: "binding-1", graph_node_id: "exec-node-1", atom_id: "atom-a", stage: "CONTRACT", canonical_statement: "The system shall.", sequence_number: 0 }],
  findings: [{ id: "finding-1", severity: "ERROR", code: "NODE_COUNT_MISMATCH", message: "Mismatch detected.", entity_id: "exec-node-1" }],
};

beforeEach(() => {
  currentSearchParams = new URLSearchParams();
  routerReplace.mockClear();
  createProjectRelation.mockClear();
  synthesizePlan.mockClear();
  verifyPlan.mockClear();
  vi.stubGlobal("ResizeObserver", FakeResizeObserver);
  vi.stubGlobal("localStorage", { getItem: vi.fn(() => null), setItem: vi.fn() });
  Object.defineProperty(window.navigator, "onLine", { configurable: true, value: true });
  window.matchMedia = fakeMatchMedia;

  getProject.mockResolvedValue({ body: { id: "project-1", name: "Demo Project" } });
  getPlanningWorkspace.mockResolvedValue({
    body: {
      latest_plan: { id: "plan-1", status: "VERIFIED" },
      counts: { authority_relations: 2, plans: 1, draft_plans: 0, blocked_plans: 0, verified_plans: 1, authority_nodes: 2, authority_edges: 0, execution_nodes: 1, execution_edges: 0, ready_nodes: 1, blocked_nodes: 0 },
    },
  });
  listProjectRelations.mockResolvedValue({
    body: { items: [{ id: "rel-1", from_atom_id: "atom-a", to_atom_id: "atom-b", relation_type: "REQUIRES", confidence: 0.8, inferred: false }] },
  });
  listProjectPlans.mockResolvedValue({ body: { items: [{ id: "plan-1", status: "VERIFIED", created_at: "2026-01-01T00:00:00Z" }] } });
  getPlan.mockResolvedValue({ body: PLAN_DETAIL });
});

describe("GraphWorkspace planning integration", () => {
  it("renders the planning overview with real counts and latest plan status", async () => {
    renderGraph();
    expect(await screen.findByText(/Authority relations/)).toBeInTheDocument();
    expect(screen.getByText("VERIFIED")).toBeInTheDocument();
  });

  it("creates a relation, shows a cycle advisory, and announces success without inventing a confidence value", async () => {
    createProjectRelation.mockResolvedValue({ body: { id: "rel-new", from_atom_id: "atom-a", to_atom_id: "atom-b", relation_type: "REQUIRES" } });
    renderGraph();
    fireEvent.click(await screen.findByRole("tab", { name: "Relations" }));
    fireEvent.change(screen.getByLabelText("Source atom"), { target: { value: "atom-a" } });
    fireEvent.change(screen.getByLabelText("Target atom"), { target: { value: "atom-b" } });
    expect(await screen.findByText(/No cycle found in the loaded subset/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create relation" }));
    await waitFor(() => expect(createProjectRelation).toHaveBeenCalledWith(expect.anything(), "project-1", expect.objectContaining({ from_atom_id: "atom-a", to_atom_id: "atom-b", relation_type: "REQUIRES" })));
  });

  it("rejects a self-referencing relation client-side before calling the server", async () => {
    renderGraph();
    fireEvent.click(await screen.findByRole("tab", { name: "Relations" }));
    fireEvent.change(screen.getByLabelText("Source atom"), { target: { value: "atom-a" } });
    fireEvent.change(screen.getByLabelText("Target atom"), { target: { value: "atom-a" } });
    fireEvent.click(screen.getByRole("button", { name: "Create relation" }));
    expect(await screen.findByText("Source and target atoms must be different.")).toBeInTheDocument();
    expect(createProjectRelation).not.toHaveBeenCalled();
  });

  it("requires an explicit open-research choice before enabling synthesis", async () => {
    renderGraph();
    fireEvent.click(await screen.findByRole("tab", { name: "Plans" }));
    expect(screen.getByRole("button", { name: "Synthesize plan" })).toBeDisabled();
    fireEvent.click(screen.getByLabelText(/Require all research resolved/));
    expect(screen.getByRole("button", { name: "Synthesize plan" })).toBeEnabled();
  });

  it("synthesizes a plan, puts the returned plan ID in the URL, and preserves the graph on failure", async () => {
    synthesizePlan.mockResolvedValue({ location: "/v1/operations/op-1", body: { operation: { state: "RUNNING" } } });
    pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED", result: { plan_id: "plan-new", status: "DRAFT" } } } });
    renderGraph();
    fireEvent.click(await screen.findByRole("tab", { name: "Plans" }));
    fireEvent.click(screen.getByLabelText(/Require all research resolved/));
    fireEvent.click(screen.getByRole("button", { name: "Synthesize plan" }));
    await waitFor(() => expect(routerReplace).toHaveBeenCalledWith(expect.stringContaining("plan=plan-new"), { scroll: false }));
  });

  it("verifies the selected plan and displays real findings with severity filtering and node focus", async () => {
    currentSearchParams = new URLSearchParams({ mode: "execution" });
    verifyPlan.mockResolvedValue({ location: "/v1/operations/op-2", body: { operation: { state: "RUNNING" } } });
    pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED", result: { plan_id: "plan-1", status: "VERIFIED" } } } });
    renderGraph();
    fireEvent.click(await screen.findByRole("tab", { name: "Verification" }));
    expect(await screen.findByText("NODE_COUNT_MISMATCH")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Verify plan" }));
    await waitFor(() => expect(verifyPlan).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "Errors (1)" }));
    expect(screen.getByText("NODE_COUNT_MISMATCH")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Focus in graph" }));
    await waitFor(() => expect(routerReplace).toHaveBeenCalledWith(expect.stringContaining("selected=exec-node-1"), { scroll: false }));
  });

  it("shows execution-mode server-ready status and plan bindings, not derived readiness", async () => {
    currentSearchParams = new URLSearchParams({ mode: "execution", selected: "exec-node-1" });
    renderGraph();
    expect(await screen.findByText("Server-ready")).toBeInTheDocument();
    expect(screen.getByText("The system shall.")).toBeInTheDocument();
  });

  it("honestly reports an explicitly selected plan that cannot be found instead of silently substituting another plan", async () => {
    currentSearchParams = new URLSearchParams({ plan: "missing-plan" });
    getPlan.mockRejectedValueOnce(new Error("not found"));
    renderGraph();
    expect(await screen.findByText("Selected plan unavailable")).toBeInTheDocument();
  });

  it("preserves the previous graph and shows an error when relation creation fails", async () => {
    createProjectRelation.mockRejectedValue(new Error("Conflict"));
    renderGraph();
    fireEvent.click(await screen.findByRole("tab", { name: "Relations" }));
    fireEvent.change(screen.getByLabelText("Source atom"), { target: { value: "atom-a" } });
    fireEvent.change(screen.getByLabelText("Target atom"), { target: { value: "atom-b" } });
    fireEvent.click(screen.getByRole("button", { name: "Create relation" }));
    expect(await screen.findByText("Action failed")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Demo Project" })).toBeInTheDocument();
  });

  it("does not render any Group 16 handoff, execution-run, provider, or routing controls", async () => {
    renderGraph();
    await screen.findByText(/Authority relations/);
    for (const label of ["Start execution run", "Provider", "Route decision", "Export", "Handoff", "Receipt", "Worker lease"]) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });
});
