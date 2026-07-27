import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SpecGraphApiError } from "@/lib/api/errors";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { stubMatchMedia } from "@/test/match-media";
import { RoutingWorkspace } from "./routing-workspace";

const getHandoffWorkspace = vi.fn();
const getRoutingPolicy = vi.fn();
const setRoutingPolicy = vi.fn();
const listProjectProviders = vi.fn();
const createOrUpdateProvider = vi.fn();
const recordProviderHealth = vi.fn();
const listProjectRenderers = vi.fn();
const createOrUpdateRenderer = vi.fn();
const selectProjectRenderer = vi.fn();
const grantProjectPaidUnlock = vi.fn();
const createProjectRouteDecision = vi.fn();
const getRouteDecision = vi.fn();

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({ createIdempotencyKey: () => "idempotency-key" }),
}));

vi.mock("@/lib/handoff/api", () => ({
  getHandoffWorkspace: (...args: unknown[]) => getHandoffWorkspace(...args),
}));

vi.mock("@/lib/routing/api", () => ({
  getRoutingPolicy: (...args: unknown[]) => getRoutingPolicy(...args),
  setRoutingPolicy: (...args: unknown[]) => setRoutingPolicy(...args),
  listProjectProviders: (...args: unknown[]) => listProjectProviders(...args),
  createOrUpdateProvider: (...args: unknown[]) => createOrUpdateProvider(...args),
  recordProviderHealth: (...args: unknown[]) => recordProviderHealth(...args),
  listProjectRenderers: (...args: unknown[]) => listProjectRenderers(...args),
  createOrUpdateRenderer: (...args: unknown[]) => createOrUpdateRenderer(...args),
  selectProjectRenderer: (...args: unknown[]) => selectProjectRenderer(...args),
  grantProjectPaidUnlock: (...args: unknown[]) => grantProjectPaidUnlock(...args),
  createProjectRouteDecision: (...args: unknown[]) => createProjectRouteDecision(...args),
  getRouteDecision: (...args: unknown[]) => getRouteDecision(...args),
}));

function renderRouting(projectId = "project-1") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RoutingWorkspace projectId={projectId} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  stubMatchMedia();
  Object.defineProperty(window.navigator, "onLine", { configurable: true, value: true });
  getHandoffWorkspace.mockResolvedValue({ body: { project: { id: "project-1", name: "Demo Project" } } });
  getRoutingPolicy.mockResolvedValue({
    etag: "policy-etag-1",
    body: { allow_offline_degraded: false, paid_emergency_enabled: false, max_paid_decisions_per_unlock: 1 },
  });
  listProjectProviders.mockResolvedValue({
    body: { items: [{ id: "provider-1", name: "Primary provider", provider_class: "cloud", cost_class: "", status: "READY", enabled: true, etag: "provider-etag-1" }] },
  });
  listProjectRenderers.mockResolvedValue({
    body: { items: [{ id: "renderer-1", name: "Primary renderer", renderer_type: "svg", enabled: true, etag: "renderer-etag-1" }] },
  });
});

describe("RoutingWorkspace", () => {
  it("renders real workspace state without fabricating data", async () => {
    renderRouting();
    expect(await screen.findByRole("heading", { name: "Demo Project" })).toBeInTheDocument();
  });

  it("shows an offline state and blocks routing actions while offline", async () => {
    Object.defineProperty(window.navigator, "onLine", { configurable: true, value: false });
    renderRouting();
    expect(await screen.findByText("Network unavailable")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save policy" })).not.toBeInTheDocument();
  });

  it("recovers from a workspace load error with a retry action", async () => {
    getHandoffWorkspace.mockRejectedValueOnce(new Error("boom"));
    renderRouting();
    expect(await screen.findByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("saves the routing policy with the loaded ETag and shows a conflict on stale write, without optimistic overwrite", async () => {
    setRoutingPolicy.mockRejectedValueOnce(new SpecGraphApiError(412, "PRECONDITION_FAILED", "Precondition failed", "req-1"));
    renderRouting();
    fireEvent.click(await screen.findByRole("button", { name: "Save policy" }));
    await waitFor(() =>
      expect(setRoutingPolicy).toHaveBeenCalledWith(
        expect.anything(),
        "project-1",
        expect.objectContaining({ allow_offline_degraded: false }),
        "policy-etag-1",
      ),
    );
    expect(await screen.findByText("Conflict")).toBeInTheDocument();
    expect(screen.getByText(/changed on the server since it was loaded/)).toBeInTheDocument();
  });

  it("creates a provider with a fresh idempotency key and no If-Match", async () => {
    createOrUpdateProvider.mockResolvedValue({ body: { id: "provider-2" }, etag: "provider-etag-2" });
    renderRouting();
    fireEvent.click(await screen.findByRole("tab", { name: "Providers" }));
    fireEvent.click(screen.getByRole("button", { name: "Add provider" }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "New provider" } });
    fireEvent.change(screen.getByLabelText("Provider class"), { target: { value: "cloud" } });
    fireEvent.change(screen.getByLabelText("Cost class"), { target: { value: "standard" } });
    fireEvent.click(screen.getByRole("button", { name: "Create provider" }));
    await waitFor(() =>
      expect(createOrUpdateProvider).toHaveBeenCalledWith(
        expect.anything(),
        "project-1",
        expect.objectContaining({ name: "New provider" }),
        "idempotency-key",
        undefined,
      ),
    );
  });

  it("records provider health as a real backend action, not a client-inferred status", async () => {
    recordProviderHealth.mockResolvedValue({ body: {} });
    renderRouting();
    fireEvent.click(await screen.findByRole("tab", { name: "Providers" }));
    fireEvent.change(await screen.findByLabelText("Record health status for Primary provider"), { target: { value: "DEGRADED" } });
    await waitFor(() =>
      expect(recordProviderHealth).toHaveBeenCalledWith(expect.anything(), "provider-1", { status: "DEGRADED" }, "idempotency-key"),
    );
  });

  it("selects a renderer for a territory using the real renderer-select endpoint", async () => {
    selectProjectRenderer.mockResolvedValue({ body: { renderer: { id: "renderer-1" } } });
    renderRouting();
    fireEvent.click(await screen.findByRole("tab", { name: "Renderers" }));
    fireEvent.change(screen.getByLabelText("Territory"), { target: { value: "us-east" } });
    fireEvent.click(screen.getByRole("button", { name: "Select renderer for territory" }));
    await waitFor(() =>
      expect(selectProjectRenderer).toHaveBeenCalledWith(expect.anything(), "project-1", { territory: "us-east" }, "idempotency-key"),
    );
  });

  it("blocks paid-unlock confirmation when the selected provider has no known cost class", async () => {
    renderRouting();
    fireEvent.click(await screen.findByRole("tab", { name: "Unlocks" }));
    fireEvent.change(screen.getByLabelText("Provider"), { target: { value: "provider-1" } });
    expect(screen.getByText(/does not report a cost class/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Actor ID"), { target: { value: "actor-1" } });
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Emergency failover" } });
    expect(screen.getByRole("button", { name: "Review unlock" })).toBeDisabled();
    expect(grantProjectPaidUnlock).not.toHaveBeenCalled();
  });

  it("requires deliberate confirmation before granting a paid unlock when cost is known", async () => {
    listProjectProviders.mockResolvedValue({
      body: { items: [{ id: "provider-1", name: "Primary provider", provider_class: "cloud", cost_class: "premium", status: "READY", enabled: true, etag: "provider-etag-1" }] },
    });
    grantProjectPaidUnlock.mockResolvedValue({ body: { id: "unlock-1", expires_at: "2099-01-01T00:00:00Z" } });
    const { container } = renderRouting();
    fireEvent.click(await screen.findByRole("tab", { name: "Unlocks" }));
    fireEvent.change(screen.getByLabelText("Provider"), { target: { value: "provider-1" } });
    fireEvent.change(screen.getByLabelText("Actor ID"), { target: { value: "actor-1" } });
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Emergency failover" } });
    fireEvent.click(screen.getByRole("button", { name: "Review unlock" }));
    expect(screen.getByText("Confirm paid route unlock")).toBeInTheDocument();
    expect(grantProjectPaidUnlock).not.toHaveBeenCalled();
    await expectNoSeriousAxeViolations(container);
    fireEvent.click(screen.getByRole("button", { name: "Confirm and grant unlock" }));
    await waitFor(() => expect(grantProjectPaidUnlock).toHaveBeenCalledWith(expect.anything(), "project-1", expect.objectContaining({ actor_id: "actor-1" }), "idempotency-key"));
    expect(await screen.findByText("Unlock granted")).toBeInTheDocument();
  });

  it("creates and looks up route decisions with no client-side routing authority", async () => {
    createProjectRouteDecision.mockResolvedValue({ body: { id: "decision-1", status: "DECIDED", selected_provider_id: "provider-1", selected_renderer_id: "renderer-1", reason_code: "HEALTHY" } });
    getRouteDecision.mockResolvedValue({ body: { id: "decision-2", status: "DECIDED", selected_provider_id: "provider-2" } });
    renderRouting();
    fireEvent.click(await screen.findByRole("tab", { name: "Decisions" }));
    fireEvent.change(screen.getByLabelText("Territory"), { target: { value: "eu-west" } });
    fireEvent.click(screen.getByRole("button", { name: "Create route decision" }));
    await waitFor(() => expect(createProjectRouteDecision).toHaveBeenCalledWith(expect.anything(), "project-1", expect.objectContaining({ territory: "eu-west" }), "idempotency-key"));
    expect(await screen.findByText(/server's real decision/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Look up decision by ID"), { target: { value: "decision-2" } });
    fireEvent.click(screen.getByRole("button", { name: "Look up" }));
    await waitFor(() => expect(getRouteDecision).toHaveBeenCalledWith(expect.anything(), "decision-2"));
    expect(await screen.findAllByText("provider-2")).not.toHaveLength(0);
  });
});
