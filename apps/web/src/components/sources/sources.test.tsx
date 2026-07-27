import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { stubMatchMedia } from "@/test/match-media";
import { DocumentInspector } from "./document-inspector";
import { SourceWorkspace } from "./source-workspace";

const getSourceWorkspace = vi.fn();
const listDocuments = vi.fn();
const getDocument = vi.fn();
const getDocumentProvenance = vi.fn();
const listDocumentAtoms = vi.fn();
const extractDocument = vi.fn();
const pollOperation = vi.fn();
const getOperations = vi.fn();
const getProject = vi.fn();
const getWorkspace = vi.fn();
const getReadiness = vi.fn();

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({
    createIdempotencyKey: () => "1234567890abcdef",
    pollOperation,
  }),
  getOperations: (...args: unknown[]) => getOperations(...args),
  getProject: (...args: unknown[]) => getProject(...args),
  getWorkspace: (...args: unknown[]) => getWorkspace(...args),
  getReadiness: (...args: unknown[]) => getReadiness(...args),
}));

vi.mock("@/lib/sources/api", () => ({
  getSourceWorkspace: (...args: unknown[]) => getSourceWorkspace(...args),
  listDocuments: (...args: unknown[]) => listDocuments(...args),
  getDocument: (...args: unknown[]) => getDocument(...args),
  getDocumentProvenance: (...args: unknown[]) => getDocumentProvenance(...args),
  listDocumentAtoms: (...args: unknown[]) => listDocumentAtoms(...args),
  extractDocument: (...args: unknown[]) => extractDocument(...args),
}));

function renderSource(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  stubMatchMedia();
  vi.stubGlobal("localStorage", { setItem: vi.fn(), getItem: vi.fn() });
  getSourceWorkspace.mockResolvedValue({ body: { documents_count: 1, uploads_count: 0, atoms_count: 2 } });
  listDocuments.mockResolvedValue({
    body: { items: [{ id: "doc-1", title: "Spec", media_type: "text/markdown", byte_length: 128, content_sha256: "a".repeat(64) }] },
    pagination: { hasMore: false, count: 1 },
  });
  getProject.mockResolvedValue({ body: { id: "project-1", name: "Project" } });
  getWorkspace.mockResolvedValue({ body: {} });
  getReadiness.mockResolvedValue({ body: { readiness: "EXTRACTION_REQUIRED" } });
  getOperations.mockResolvedValue({ body: { items: [{ id: "op-1", operation_type: "finalize_source_upload", state: "RUNNING", progress_current: 1, progress_total: 2 }] } });
  getDocument.mockResolvedValue({ body: { id: "doc-1", title: "Spec", media_type: "text/markdown", content: "<b>plain text only</b>", content_sha256: "b".repeat(64) } });
  getDocumentProvenance.mockResolvedValue({
    body: {
      provenance: {
        raw_authority: { byte_count: 128, sha256: "a".repeat(64), original_media_type: "text/markdown" },
        derivation: { adapter_name: "markdown", adapter_version: "1", derived_sha256: "b".repeat(64), locators_preview: [{ ordinal: 1, label: "section 1" }] },
      },
    },
  });
  listDocumentAtoms.mockResolvedValue({ body: { items: [{ id: "atom-1", text: "Authority is preserved", line_start: 1, line_end: 1 }] }, pagination: { hasMore: false } });
  extractDocument.mockResolvedValue({ location: "/v1/operations/op-1", body: { operation: { state: "RUNNING" } } });
  pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED" } } });
});

describe("source workspace", () => {
  it("renders a premium bounded source library and activity from real API data", async () => {
    renderSource(<SourceWorkspace projectId="project-1" />);
    expect(await screen.findByRole("heading", { name: "Immutable source observatory" })).toBeInTheDocument();
    expect(screen.getByText("Spec")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "Activity" }));
    expect(screen.getByText("finalize_source_upload RUNNING")).toBeInTheDocument();
  });

  it("renders document authority, derived text, atoms, and provenance without HTML execution", async () => {
    renderSource(<DocumentInspector projectId="project-1" documentId="doc-1" />);
    expect(await screen.findByRole("heading", { name: "Spec" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "Derived text" }));
    expect(screen.getByText("<b>plain text only</b>")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "Provenance" }));
    expect(screen.getAllByText("Raw authority").length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole("button", { name: /Extract atoms/ }));
    expect(await screen.findByText("Extraction SUCCEEDED.")).toBeInTheDocument();
  });

  it("has no serious or critical axe violations", async () => {
    const { container } = renderSource(<SourceWorkspace projectId="project-1" />);
    await screen.findByRole("heading", { name: "Immutable source observatory" });
    await expectNoSeriousAxeViolations(container);
  });
});
