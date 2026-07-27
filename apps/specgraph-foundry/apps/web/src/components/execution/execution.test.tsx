import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { ExecutionRunDetail } from "./execution-run-detail";

const getExecutionRun = vi.fn();
const verifyExecutionRun = vi.fn();
const pollOperation = vi.fn();

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({ createIdempotencyKey: () => "idempotency-key", pollOperation }),
}));

vi.mock("@/lib/execution/api", () => ({
  getExecutionRun: (...args: unknown[]) => getExecutionRun(...args),
  verifyExecutionRun: (...args: unknown[]) => verifyExecutionRun(...args),
}));

function renderRunDetail(projectId = "project-1", runId = "run-1") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ExecutionRunDetail projectId={projectId} runId={runId} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  Object.defineProperty(window.navigator, "onLine", { configurable: true, value: true });
  getExecutionRun.mockResolvedValue({
    body: {
      id: "run-1",
      runtime_system: "atropos",
      runtime_run_id: "run-xyz",
      status: "RUNNING",
      nodes: [
        {
          id: "node-1",
          stage: "IMPLEMENTATION",
          title: "Implement handler",
          status: "CLAIMED",
          lease_owner: "worker-abc",
        },
      ],
      ready_nodes: [{ id: "node-2" }],
      receipts: [
        {
          id: "receipt-1",
          run_node_id: "node-1",
          actor_system: "atropos",
          actor_id: "worker-abc",
          outcome: "ACCEPTED",
          summary: "Implementation matches contract.",
          evidence_sha256: "deadbeef",
          validation_status: "VALID",
          evidence: { secretPayload: "do-not-render", another: "field" },
        },
      ],
      findings: [{ id: "finding-1", severity: "WARNING", code: "SG-100", message: "Minor drift detected." }],
    },
  });
});

describe("ExecutionRunDetail", () => {
  it("renders real run detail using only safe returned fields", async () => {
    renderRunDetail();
    expect(await screen.findByRole("heading", { name: /atropos/ })).toBeInTheDocument();
    expect(screen.getByText("RUNNING")).toBeInTheDocument();
    expect(screen.getByText("run-xyz")).toBeInTheDocument();
    expect(screen.getByText("Implement handler")).toBeInTheDocument();
    expect(screen.getByText("worker-abc")).toBeInTheDocument();
  });

  it("shows an offline state and does not render run controls while offline", async () => {
    Object.defineProperty(window.navigator, "onLine", { configurable: true, value: false });
    renderRunDetail();
    expect(await screen.findByText("Network unavailable")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Verify run" })).not.toBeInTheDocument();
  });

  it("recovers from a run load error with a retry action", async () => {
    getExecutionRun.mockRejectedValueOnce(new Error("boom"));
    renderRunDetail();
    expect(await screen.findByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("never renders raw receipt evidence, lease tokens, or worker credentials, only safe metadata", async () => {
    const { container } = renderRunDetail();
    await screen.findByRole("heading", { name: /atropos/ });
    expect(container.innerHTML).not.toContain("secretPayload");
    expect(container.innerHTML).not.toContain("do-not-render");
    expect(screen.getByText("deadbeef")).toBeInTheDocument();
    expect(screen.getByText(/2 field\(s\) attached/)).toBeInTheDocument();
    expect(screen.getByText("ACCEPTED")).toBeInTheDocument();
  });

  it("marks a node ready only from the server ready_nodes list, never client-inferred", async () => {
    renderRunDetail();
    const table = await screen.findByRole("table", { name: "Execution nodes" });
    expect(within(table).queryByText("Ready")).not.toBeInTheDocument();
  });

  it("does not expose worker-only claim/heartbeat/receipt-submission actions as human UI buttons", async () => {
    renderRunDetail();
    await screen.findByRole("heading", { name: /atropos/ });
    expect(screen.queryByRole("button", { name: /claim/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /heartbeat/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /submit receipt/i })).not.toBeInTheDocument();
  });

  it("verifies a run independently with a fresh idempotency key and polls to a terminal state", async () => {
    verifyExecutionRun.mockResolvedValue({ location: "/v1/operations/op-3", body: { operation: { state: "RUNNING" } } });
    pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED" } } });
    renderRunDetail();
    fireEvent.click(await screen.findByRole("button", { name: "Verify run" }));
    await waitFor(() => expect(verifyExecutionRun).toHaveBeenCalledWith(expect.anything(), "run-1", "idempotency-key"));
    await waitFor(() => expect(pollOperation).toHaveBeenCalledWith("/v1/operations/op-3", expect.objectContaining({ onProgress: expect.any(Function) })));
  });

  it("preserves prior run state when verification fails", async () => {
    verifyExecutionRun.mockRejectedValueOnce(new Error("Verification rejected"));
    renderRunDetail();
    fireEvent.click(await screen.findByRole("button", { name: "Verify run" }));
    expect(await screen.findByText("Verification failed")).toBeInTheDocument();
    expect(screen.getByText("RUNNING")).toBeInTheDocument();
  });

  it("has no serious or critical axe violations", async () => {
    const { container } = renderRunDetail();
    await screen.findByRole("heading", { name: /atropos/ });
    await expectNoSeriousAxeViolations(container);
  });
});
