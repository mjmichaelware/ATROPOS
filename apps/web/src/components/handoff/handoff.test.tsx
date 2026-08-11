import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { stubMatchMedia } from "@/test/match-media";
import { HandoffWorkspace } from "./handoff-workspace";

const currentSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => currentSearchParams,
}));

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
const listProjectPlans = vi.fn();

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

vi.mock("@/lib/graph/api", () => ({
  listProjectPlans: (...args: unknown[]) => listProjectPlans(...args),
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
  listProjectPlans.mockResolvedValue({
    body: {
      items: [
        { id: "plan-1", status: "VERIFIED", created_at: "2026-01-01T00:00:00Z" },
        { id: "plan-2", status: "VERIFIED", created_at: "2026-01-02T00:00:00Z" },
      ],
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
    fireEvent.change(await screen.findByLabelText("Plan to export"), { target: { value: "plan-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate export" }));
    await waitFor(() => expect(exportPlan).toHaveBeenCalledWith(expect.anything(), "plan-1", undefined, "idempotency-key"));
    await waitFor(() => expect(pollOperation).toHaveBeenCalledWith("/v1/operations/op-1", expect.objectContaining({ onProgress: expect.any(Function) })));
  });

  it("only allows downloading a verified export, fetches links automatically, and never persists the signed URL beyond the click", async () => {
    // Regression test: this used to require an extra "Request download
    // links" click before the real download buttons even appeared - a
    // pointless step since the request is a plain, side-effect-free GET.
    // Links must now be fetched the moment the panel mounts.
    getExport.mockResolvedValue({ body: { id: "export-1", status: "VERIFIED", artifact_manifest: { state: "VERIFIED", artifact_count: 1, total_bytes: 100, aggregate_sha256: "abc" } } });
    downloadExportArtifacts.mockResolvedValue({
      body: { export_id: "export-1", manifest_id: "manifest-1", expires_in: 60, artifacts: [{ name: "bundle.tar", media_type: "application/x-tar", byte_length: 100, sha256: "abc", signed_download_url: "https://signed.example/x", expires_at: "2099-01-01T00:00:00Z" }] },
    });
    renderHandoff();
    fireEvent.click(await screen.findByRole("tab", { name: "Exports" }));
    // VERIFIED exports auto-expand so the download panel is visible immediately
    expect(await screen.findByRole("button", { name: "Verify export" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Request download links" })).not.toBeInTheDocument();
    await waitFor(() => expect(downloadExportArtifacts).toHaveBeenCalledWith(expect.anything(), "export-1"));
    expect(await screen.findByText("bundle.tar")).toBeInTheDocument();
    expect(localStorage.getItem("bundle-download")).toBeNull();
    expect(sessionStorage.getItem("bundle-download")).toBeNull();
  });

  it("offers the build plan PDF/text as a direct one-click download, not a generic label", async () => {
    getExport.mockResolvedValue({ body: { id: "export-1", status: "VERIFIED", artifact_manifest: { state: "VERIFIED", artifact_count: 2, total_bytes: 100, aggregate_sha256: "abc" } } });
    downloadExportArtifacts.mockResolvedValue({
      body: {
        export_id: "export-1",
        manifest_id: "manifest-1",
        expires_in: 60,
        artifacts: [
          { name: "implementation_blueprint.pdf", media_type: "application/pdf", byte_length: 100, sha256: "pdf", signed_download_url: "https://signed.example/pdf", expires_at: "2099-01-01T00:00:00Z" },
          { name: "implementation_blueprint.txt", media_type: "text/plain", byte_length: 100, sha256: "txt", signed_download_url: "https://signed.example/txt", expires_at: "2099-01-01T00:00:00Z" },
        ],
      },
    });
    renderHandoff();
    fireEvent.click(await screen.findByRole("tab", { name: "Exports" }));
    // VERIFIED exports auto-expand, so the download buttons are immediately visible
    expect(await screen.findByRole("button", { name: "Download build plan (PDF)" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download build plan (text)" })).toBeInTheDocument();
  });

  it("offers a retry when the automatic download-link fetch fails, instead of a dead end", async () => {
    // Regression test (Codex review on PR #68): the automatic fetch only
    // ever fires once per exportId, and the retry button used to be
    // hidden whenever there were zero artifacts - which is exactly the
    // state a failed first fetch leaves you in. That combination meant a
    // transient failure had no recovery path short of collapsing and
    // re-expanding the export row.
    getExport.mockResolvedValue({ body: { id: "export-1", status: "VERIFIED", artifact_manifest: { state: "VERIFIED", artifact_count: 1, total_bytes: 100, aggregate_sha256: "abc" } } });
    downloadExportArtifacts.mockRejectedValueOnce(new Error("signed download unavailable"));
    renderHandoff();
    fireEvent.click(await screen.findByRole("tab", { name: "Exports" }));
    // VERIFIED exports auto-expand, so the error surfaces without any click
    expect(await screen.findByText("Download unavailable")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Refresh links" });

    downloadExportArtifacts.mockResolvedValue({
      body: { export_id: "export-1", manifest_id: "manifest-1", expires_in: 60, artifacts: [{ name: "bundle.tar", media_type: "application/x-tar", byte_length: 100, sha256: "abc", signed_download_url: "https://signed.example/x", expires_at: "2099-01-01T00:00:00Z" }] },
    });
    fireEvent.click(retry);
    expect(await screen.findByText("bundle.tar")).toBeInTheDocument();
  });

  it("starts an execution run only from an explicit plan/runtime input, never from client-inferred eligibility", async () => {
    startExecutionRun.mockResolvedValue({ location: "/v1/operations/op-2", body: { operation: { state: "RUNNING" } } });
    pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED" } } });
    renderHandoff();
    fireEvent.click(await screen.findByRole("tab", { name: "Runs" }));
    fireEvent.change(await screen.findByLabelText("Plan to run"), { target: { value: "plan-2" } });
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
