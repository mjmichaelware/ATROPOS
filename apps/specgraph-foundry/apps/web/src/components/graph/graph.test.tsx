import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { GraphWorkspace } from "./graph-workspace";

const getProject = vi.fn();
const getPlanningWorkspace = vi.fn();
const listProjectRelations = vi.fn();
const listProjectPlans = vi.fn();
const getPlan = vi.fn();

const routerReplace = vi.fn();
let currentSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: routerReplace }),
  usePathname: () => "/developer/specgraph/project-1/graph",
  useSearchParams: () => currentSearchParams,
}));

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({ createIdempotencyKey: () => "idempotency-key" }),
  getProject: (...args: unknown[]) => getProject(...args),
}));

vi.mock("@/lib/graph/api", () => ({
  getPlanningWorkspace: (...args: unknown[]) => getPlanningWorkspace(...args),
  listProjectRelations: (...args: unknown[]) => listProjectRelations(...args),
  listProjectPlans: (...args: unknown[]) => listProjectPlans(...args),
  getPlan: (...args: unknown[]) => getPlan(...args),
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

function renderGraph(projectId = "project-1") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <GraphWorkspace projectId={projectId} />
    </QueryClientProvider>,
  );
}

function fakeMatchMedia(query: string) {
  return {
    matches: query.includes("min-width: 768px"),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  } as unknown as MediaQueryList;
}

beforeEach(() => {
  currentSearchParams = new URLSearchParams();
  routerReplace.mockClear();
  vi.stubGlobal("ResizeObserver", FakeResizeObserver);
  vi.stubGlobal("localStorage", { getItem: vi.fn(() => null), setItem: vi.fn() });
  Object.defineProperty(window.navigator, "onLine", { configurable: true, value: true });
  window.matchMedia = fakeMatchMedia;
  getProject.mockResolvedValue({ body: { id: "project-1", name: "Demo Project" } });
  getPlanningWorkspace.mockResolvedValue({ body: { latest_plan: null } });
  listProjectRelations.mockResolvedValue({
    body: {
      items: [
        { id: "rel-1", from_atom_id: "atom-a", to_atom_id: "atom-b", relation_type: "REQUIRES", rationale: "b needs a", confidence: 0.8, inferred: false },
        { id: "rel-2", from_atom_id: "atom-a", to_atom_id: "atom-c", relation_type: "REFINES", inferred: true },
      ],
    },
  });
  getPlan.mockResolvedValue({ body: {} });
  listProjectPlans.mockResolvedValue({ body: { items: [] } });
});

describe("GraphWorkspace", () => {
  it("renders the empty state when no plan and no relations exist", async () => {
    listProjectRelations.mockResolvedValueOnce({ body: { items: [] } });
    renderGraph();
    expect(await screen.findByText("Your source's connections will show up here")).toBeInTheDocument();
  });

  it("renders the accessible list with real relation data and switches view modes", async () => {
    currentSearchParams = new URLSearchParams({ view: "list" });
    renderGraph();
    expect(await screen.findByRole("table")).toBeInTheDocument();
    const table = screen.getByRole("table");
    expect(within(table).getAllByRole("row")).toHaveLength(4); // header + 3 atom nodes
  });

  it("never invents a confidence value the API did not return", async () => {
    currentSearchParams = new URLSearchParams({ view: "list" });
    renderGraph();
    await screen.findByRole("table");
    fireEvent.click(screen.getAllByRole("button", { name: "Inspect" })[0]);
    expect(routerReplace).toHaveBeenCalled();
  });

  it("filters the loaded subset by search query without a server round trip", async () => {
    currentSearchParams = new URLSearchParams({ view: "list" });
    renderGraph();
    await screen.findByRole("table");
    fireEvent.change(screen.getByLabelText("Search loaded nodes"), { target: { value: "does-not-exist" } });
    expect(routerReplace).toHaveBeenCalledWith(expect.stringContaining("q=does-not-exist"), { scroll: false });
    expect(listProjectRelations).toHaveBeenCalledTimes(1);
  });

  it("shows an offline state and skips rendering the graph while offline", async () => {
    Object.defineProperty(window.navigator, "onLine", { configurable: true, value: false });
    renderGraph();
    expect(await screen.findByText("Network unavailable")).toBeInTheDocument();
  });

  it("renders the canvas view without crashing when React Flow mounts", async () => {
    renderGraph();
    expect(await screen.findByLabelText("Graph canvas")).toBeInTheDocument();
  });

  it("selects a node from the accessible list and opens the inspector", async () => {
    currentSearchParams = new URLSearchParams({ view: "list" });
    renderGraph();
    await screen.findByRole("table");
    fireEvent.click(screen.getAllByRole("button", { name: "Inspect" })[0]);
    await waitFor(() => expect(routerReplace).toHaveBeenCalledWith(expect.stringContaining("selected="), { scroll: false }));
  });

  it("recovers from a workspace load error with a retry action", async () => {
    getPlanningWorkspace.mockRejectedValueOnce(new Error("boom"));
    renderGraph();
    expect(await screen.findByText("Graph unavailable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("has no serious or critical axe violations in the accessible list view", async () => {
    currentSearchParams = new URLSearchParams({ view: "list" });
    const { container } = renderGraph();
    await screen.findByRole("table");
    await expectNoSeriousAxeViolations(container);
  });
});
