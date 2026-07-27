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
  getWorkspace.mockResolvedValue({ body: { sources_count: 1, atoms_count: 2, latest_document: { title: "Doc" } } });
  getReadiness.mockResolvedValue({ body: { readiness: "READY_TO_PLAN" } });
  getOperations.mockResolvedValue({ body: { items: [{ id: "op", state: "QUEUED", operation_type: "extract_document_atoms" }] } });
});

describe("projects experience", () => {
  it("renders paginated project directory and next navigation", async () => {
    renderProject(<ProjectDirectory />);
    expect(await screen.findByText("Alpha")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(listProjects).toHaveBeenCalled();
  });

  it("creates a project and navigates to its command center", async () => {
    renderProject(<ProjectCreateForm />);
    await userEvent.type(screen.getByLabelText("Slug"), "new-project");
    await userEvent.type(screen.getByLabelText("Name"), "New Project");
    await userEvent.click(screen.getByRole("button", { name: "Create project" }));
    await waitFor(() => {
      expect(push).toHaveBeenCalledWith("/projects/22222222-2222-2222-2222-222222222222");
    });
  });

  it("renders command center from authoritative API data", async () => {
    renderProject(<ProjectCommandCenter projectId="11111111-1111-1111-1111-111111111111" />);
    expect(await screen.findByRole("heading", { name: "Alpha" })).toBeInTheDocument();
    expect(screen.getByText("Ready to plan")).toBeInTheDocument();
    expect(screen.getByText("extract_document_atoms")).toBeInTheDocument();
  });

  it("has no serious or critical axe violations on the command center", async () => {
    const { container } = renderProject(<ProjectCommandCenter projectId="11111111-1111-1111-1111-111111111111" />);
    await screen.findByRole("heading", { name: "Alpha" });
    await expectNoSeriousAxeViolations(container);
  });
});
