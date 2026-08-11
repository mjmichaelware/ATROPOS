import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { stubMatchMedia } from "@/test/match-media";
import { ResearchWorkspace } from "./research-workspace";
import { TaskInspector } from "./task-inspector";

const getResearchWorkspace = vi.fn();
const getGapMatrix = vi.fn();
const listResearchTasks = vi.fn();
const getResearchTask = vi.fn();
const claimResearchTask = vi.fn();
const heartbeatResearchTask = vi.fn();
const addResearchEvidence = vi.fn();
const completeResearchTask = vi.fn();
const pollOperation = vi.fn();
const getProject = vi.fn();
const getWorkspace = vi.fn();
const getReadiness = vi.fn();
const getOperations = vi.fn();

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({
    createIdempotencyKey: () => "1234567890abcdef",
    pollOperation,
  }),
  getProject: (...args: unknown[]) => getProject(...args),
  getWorkspace: (...args: unknown[]) => getWorkspace(...args),
  getReadiness: (...args: unknown[]) => getReadiness(...args),
  getOperations: (...args: unknown[]) => getOperations(...args),
}));

vi.mock("@/lib/research/api", () => ({
  getResearchWorkspace: (...args: unknown[]) => getResearchWorkspace(...args),
  getGapMatrix: (...args: unknown[]) => getGapMatrix(...args),
  listResearchTasks: (...args: unknown[]) => listResearchTasks(...args),
  getResearchTask: (...args: unknown[]) => getResearchTask(...args),
  claimResearchTask: (...args: unknown[]) => claimResearchTask(...args),
  heartbeatResearchTask: (...args: unknown[]) => heartbeatResearchTask(...args),
  addResearchEvidence: (...args: unknown[]) => addResearchEvidence(...args),
  completeResearchTask: (...args: unknown[]) => completeResearchTask(...args),
}));

function renderResearch(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.useRealTimers();
  stubMatchMedia();
  vi.stubGlobal("localStorage", { setItem: vi.fn(), getItem: vi.fn() });
  getProject.mockResolvedValue({ body: { id: "project-1", name: "Project" } });
  getWorkspace.mockResolvedValue({ body: {} });
  getReadiness.mockResolvedValue({ body: { readiness: "RESEARCH_REQUIRED" } });
  getOperations.mockResolvedValue({ body: { items: [] } });
  getResearchWorkspace.mockResolvedValue({
    body: { counts: { atoms: 2, dimensions: 3, open_dimensions: 1, resolved_dimensions: 1, not_applicable_dimensions: 1, tasks: 1, evidence: 1 } },
  });
  getGapMatrix.mockResolvedValue({
    body: {
      summary: { open_dimensions: 1, resolved_dimensions: 1, not_applicable_dimensions: 1 },
      dimensions: ["safety", "provenance"],
      atoms: [
        {
          id: "atom-1",
          text: "The system preserves provenance.",
          canonical_statement: "The system preserves provenance.",
          kind: "FUNCTIONAL",
          modality: "TEXT",
          document_id: "doc-1",
          dimensions: {
            safety: { status: "OPEN", task_id: "task-1" },
            provenance: { status: "RESOLVED", task_id: "task-2" },
          },
        },
      ],
    },
  });
  listResearchTasks.mockResolvedValue({
    body: { items: [{ id: "task-1", atom_id: "atom-1", dimension: "safety", status: "PENDING" }] },
    pagination: { hasMore: true, nextCursor: "cursor-1" },
  });
  getResearchTask.mockResolvedValue({
    body: {
      id: "task-1",
      atom_id: "atom-1",
      dimension: "safety",
      status: "PENDING",
      canonical_statement: "The system preserves provenance.",
      kind: "FUNCTIONAL",
      modality: "TEXT",
      evidence: [{ id: "ev-1", source_uri: "https://example.test/spec", source_title: "Spec", excerpt: "Evidence" }],
    },
  });
  claimResearchTask.mockResolvedValue({ body: { task: { id: "task-1", status: "CLAIMED", lease_expires_at: "2026-01-01T00:05:00Z" } } });
  heartbeatResearchTask.mockResolvedValue({ body: { id: "task-1", status: "CLAIMED" } });
  addResearchEvidence.mockResolvedValue({ body: { id: "ev-2" } });
  completeResearchTask.mockResolvedValue({ location: "/v1/operations/op-1", body: { operation: { state: "RUNNING", operation_type: "research_completion" } } });
  pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED", operation_type: "research_completion" } } });
});

describe("research workspace", () => {
  it("renders overview, gap matrix, bounded task pagination, and authority separation", async () => {
    renderResearch(<ResearchWorkspace projectId="project-1" />);
    expect(await screen.findByRole("heading", { name: "Fill in what your sources don't say yet" })).toBeInTheDocument();
    expect(screen.getByText("Source authority: immutable uploaded source and provenance.")).toBeInTheDocument();
    // Regression: the Research overview never linked to Routing, so a
    // provider-driven automation path had no discovery path from Research.
    expect(screen.getByRole("link", { name: "Routing" })).toHaveAttribute("href", "/developer/specgraph/project-1/routing");
    fireEvent.click(screen.getByRole("tab", { name: "Gap matrix" }));
    expect(screen.getByLabelText("Research gap matrix")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Task queue" }));
    expect(screen.getByText("Filter applies only to the loaded cursor page.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next cursor page" }));
    expect(listResearchTasks).toHaveBeenCalled();
  });

  it("links atom dimension badges and gap matrix cells straight to their research task", async () => {
    // Regression test: gap cells used to render as inert status badges/buttons
    // with no way to navigate to the underlying task - "I can't even open
    // them." Both the atoms-and-dimensions view and the gap matrix now link
    // through to /projects/{projectId}/research/tasks/{taskId} whenever the
    // API returns a task_id for that cell.
    renderResearch(<ResearchWorkspace projectId="project-1" />);
    expect(await screen.findByRole("heading", { name: "Fill in what your sources don't say yet" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Atoms and dimensions" }));
    // Regression: atom cards used to render the literal string "Atom" for
    // every card (a label/text vs. canonical_statement field mismatch) -
    // the card must show the atom's actual statement.
    expect(screen.getByRole("heading", { name: "The system preserves provenance." })).toBeInTheDocument();
    // Each dimension badge's accessible name includes the dimension itself
    // (not just the shared "Open"/"Resolved" status text) so screen-reader
    // and keyboard link-list users can tell multiple gap links apart.
    expect(screen.getByRole("link", { name: "safety: Open" })).toHaveAttribute("href", "/developer/specgraph/project-1/research/tasks/task-1");

    fireEvent.click(screen.getByRole("tab", { name: "Gap matrix" }));
    expect(screen.getByRole("link", { name: "Go to research task for safety" })).toHaveAttribute(
      "href",
      "/developer/specgraph/project-1/research/tasks/task-1",
    );

    // The navigation link must not swallow the pre-existing local-preview
    // interaction: selecting the cell body still updates the inspector aside.
    fireEvent.click(screen.getByRole("button", { name: /safety/ }));
    expect(await screen.findByText("safety is currently open.")).toBeInTheDocument();
  });

  it("claims a task, records evidence, requires NOT_APPLICABLE justification, and polls completion", async () => {
    renderResearch(<TaskInspector projectId="project-1" taskId="task-1" />);
    expect(await screen.findByRole("heading", { name: "safety" })).toBeInTheDocument();
    // Regression: opening a task via the "OPEN" link used to show only an
    // empty claim/evidence form with no indication of what the atom says.
    expect(screen.getByText("The system preserves provenance.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Claim task" }));
    await waitFor(() => expect(claimResearchTask).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText("Evidence URL"), { target: { value: "https://example.test/research" } });
    fireEvent.change(screen.getByLabelText("Evidence title"), { target: { value: "Research note" } });
    fireEvent.change(screen.getByLabelText("Evidence excerpt"), { target: { value: "The evidence supports the conclusion." } });
    fireEvent.click(screen.getByRole("button", { name: "Record evidence" }));
    await waitFor(() => expect(addResearchEvidence).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "Not applicable" }));
    fireEvent.click(screen.getByRole("button", { name: "Queue completion" }));
    expect(await screen.findByText(/explicit justification/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Conclusion or justification"), { target: { value: "Dimension does not apply because the atom is outside that scope." } });
    fireEvent.click(screen.getByRole("button", { name: "Queue completion" }));
    await waitFor(() => expect(completeResearchTask).toHaveBeenCalled());
    expect(pollOperation).toHaveBeenCalledWith("/v1/operations/op-1");
    expect(await screen.findByText("Completion operation SUCCEEDED.")).toBeInTheDocument();
  });

  it("has no serious or critical axe violations, including the gap matrix panel", async () => {
    const { container } = renderResearch(<ResearchWorkspace projectId="project-1" />);
    await screen.findByRole("heading", { name: "Fill in what your sources don't say yet" });
    fireEvent.click(screen.getByRole("tab", { name: "Gap matrix" }));
    expect(screen.getByLabelText("Research gap matrix")).toBeInTheDocument();
    await expectNoSeriousAxeViolations(container);
  });
});
