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
        { id: "atom-1", text: "The system preserves provenance.", document_id: "doc-1", dimensions: { safety: "OPEN", provenance: { status: "RESOLVED" } } },
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
    expect(await screen.findByRole("heading", { name: "Gap-field research instrument" })).toBeInTheDocument();
    expect(screen.getByText("Source authority: immutable uploaded source and provenance.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Gap matrix" }));
    expect(screen.getByLabelText("Research gap matrix")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Task queue" }));
    expect(screen.getByText("Filter applies only to the loaded cursor page.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next cursor page" }));
    expect(listResearchTasks).toHaveBeenCalled();
  });

  it("claims a task, records evidence, requires NOT_APPLICABLE justification, and polls completion", async () => {
    renderResearch(<TaskInspector projectId="project-1" taskId="task-1" />);
    expect(await screen.findByRole("heading", { name: "safety" })).toBeInTheDocument();
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
    await screen.findByRole("heading", { name: "Gap-field research instrument" });
    fireEvent.click(screen.getByRole("tab", { name: "Gap matrix" }));
    expect(screen.getByLabelText("Research gap matrix")).toBeInTheDocument();
    await expectNoSeriousAxeViolations(container);
  });
});
