import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { stubMatchMedia } from "@/test/match-media";
import { HandoffWorkspace } from "./handoff-workspace";

const getHandoffWorkspace = vi.fn();
const listProjectBindings = vi.fn();
const createOrUpdateBinding = vi.fn();
const listProjectExports = vi.fn();
const exportPlan = vi.fn();
const getExport = vi.fn();
const verifyExport = vi.fn();
const downloadExportArtifacts = vi.fn();
const startExecutionRun = vi.fn();
const pollOperation = vi.fn();

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({ createIdempotencyKey: () => "idempotency-key", pollOperation }),
}));

vi.mock("@/lib/handoff/api", () => ({
  getHandoffWorkspace: (...args: unknown[]) => getHandoffWorkspace(...args),
  listProjectBindings: (...args: unknown[]) => listProjectBindings(...args),
  createOrUpdateBinding: (...args: unknown[]) => createOrUpdateBinding(...args),
  listProjectExports: (...args: unknown[]) => listProjectExports(...args),
  exportPlan: (...args: unknown[]) => exportPlan(...args),
  getExport: (...args: unknown[]) => getExport(...args),
  verifyExport: (...args: unknown[]) => verifyExport(...args),
  downloadExportArtifacts: (...args: unknown[]) => downloadExportArtifacts(...args),
  startExecutionRun: (...args: unknown[]) => startExecutionRun(...args),
}));

function renderHandoff(projectId = "project-1") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <HandoffWorkspace projectId={projectId} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  stubMatchMedia();
  Object.defineProperty(window.navigator, "onLine", { configurable: true, value: true });
  getHandoffWorkspace.mockResolvedValue({
    body: {
      project: { id: "project-1", name: "Demo Project" },
      counts: { bindings: 1, enabled_bindings: 1, exports: 1, verified_exports: 1, invalid_exports: 0, execution_runs: 1, verified_execution_runs: 0, rejected_execution_runs: 0, receipts: 0, execution_findings: 0, providers: 0, ready_providers: 0, renderers: 0, enabled_renderers: 0 },
      bindings: [{ id: "binding-1", system_name: "atropos", binding_type: "REST", enabled: true, etag: "etag-1" }],
      exports: [{ id: "export-1", status: "VERIFIED" }],
      execution_runs: [{ id: "run-1", status: "RUNNING" }],
      latest_export: { id: "export-1", status: "VERIFIED" },
      latest_execution_run: { id: "run-1", status: "RUNNING" },
    },
  });
});

describe("HandoffWorkspace", () => {
  it("renders real workspace overview counts without fabricating data", async () => {
    renderHandoff();
    expect(await screen.findByRole("heading", { name: "Demo Project" })).toBeInTheDocument();
    expect(screen.getByText(/1 enabled/)).toBeInTheDocument();
  });

  it("shows an offline state and blocks mutations while offline", async () => {
    Object.defineProperty(window.navigator, "onLine", { configurable: true, value: false });
    renderHandoff();
    expect(await screen.findByText("Network unavailable")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add binding" })).not.toBeInTheDocument();
  });

  it("recovers from a workspace load error with a retry action", async () => {
    getHandoffWorkspace.mockRejectedValueOnce(new Error("boom"));
    renderHandoff();
    expect(await screen.findByText("Handoff unavailable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("creates a binding with a fresh idempotency key and no If-Match, then preserves state on failure", async () => {
    createOrUpdateBinding.mockRejectedValueOnce(new Error("Conflict"));
    renderHandoff();
    fireEvent.click(await screen.findByRole("tab", { name: "Bindings" }));
    fireEvent.click(screen.getByRole("button", { name: "Add binding" }));
    fireEvent.change(screen.getByLabelText("System name"), { target: { value: "new-system" } });
    fireEvent.change(screen.getByLabelText("Binding type"), { target: { value: "REST" } });
    fireEvent.click(screen.getByRole("button", { name: "Create binding" }));
    await waitFor(() => expect(createOrUpdateBinding).toHaveBeenCalledWith(expect.anything(), "project-1", expect.objectContaining({ system_name: "new-system" }), "idempotency-key", undefined));
    expect(await screen.findByText("Save failed")).toBeInTheDocument();
  });

  it("generates an export from a plan ID and polls the operation to a terminal state", async () => {
    exportPlan.mockResolvedValue({ location: "/v1/operations/op-1", body: { operation: { state: "RUNNING" } } });
    pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED" } } });
    renderHandoff();
    fireEvent.click(await screen.findByRole("tab", { name: "Exports" }));
    fireEvent.change(screen.getByLabelText(/Plan ID/), { target: { value: "plan-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate export" }));
    await waitFor(() => expect(exportPlan).toHaveBeenCalledWith(expect.anything(), "plan-1", undefined, "idempotency-key"));
    await waitFor(() => expect(pollOperation).toHaveBeenCalledWith("/v1/operations/op-1"));
  });

  it("only allows downloading a verified export and never persists the signed URL beyond the click", async () => {
    getExport.mockResolvedValue({ body: { id: "export-1", status: "VERIFIED", artifact_manifest: { state: "VERIFIED", artifact_count: 1, total_bytes: 100, aggregate_sha256: "abc" } } });
    downloadExportArtifacts.mockResolvedValue({
      body: { export_id: "export-1", manifest_id: "manifest-1", expires_in: 60, artifacts: [{ name: "bundle.tar", media_type: "application/x-tar", byte_length: 100, sha256: "abc", signed_download_url: "https://signed.example/x", expires_at: "2099-01-01T00:00:00Z" }] },
    });
    renderHandoff();
    fireEvent.click(await screen.findByRole("tab", { name: "Exports" }));
    fireEvent.click(screen.getByRole("button", { name: /export-1/ }));
    expect(await screen.findByRole("button", { name: "Verify export" })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole("button", { name: "Request download links" }));
    expect(await screen.findByText("bundle.tar")).toBeInTheDocument();
    expect(localStorage.getItem("bundle-download")).toBeNull();
    expect(sessionStorage.getItem("bundle-download")).toBeNull();
  });

  it("starts an execution run only from an explicit plan/runtime input, never from client-inferred eligibility", async () => {
    startExecutionRun.mockResolvedValue({ location: "/v1/operations/op-2", body: { operation: { state: "RUNNING" } } });
    pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED" } } });
    renderHandoff();
    fireEvent.click(await screen.findByRole("tab", { name: "Runs" }));
    fireEvent.change(screen.getByLabelText(/Plan ID/), { target: { value: "plan-2" } });
    fireEvent.change(screen.getByLabelText("Runtime system"), { target: { value: "atropos" } });
    fireEvent.change(screen.getByLabelText("Runtime run ID"), { target: { value: "run-xyz" } });
    fireEvent.click(screen.getByRole("button", { name: "Start execution run" }));
    await waitFor(() => expect(startExecutionRun).toHaveBeenCalledWith(expect.anything(), "plan-2", { runtime_system: "atropos", runtime_run_id: "run-xyz" }, "idempotency-key"));
  });

  it("does not render any Group 17+ visual-polish or unrelated deployment controls", async () => {
    renderHandoff();
    await screen.findByRole("heading", { name: "Demo Project" });
    for (const label of ["Deploy", "Rollback", "Publish", "Release"]) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });

  it("has no serious or critical axe violations", async () => {
    const { container } = renderHandoff();
    await screen.findByRole("heading", { name: "Demo Project" });
    await expectNoSeriousAxeViolations(container);
  });
});
