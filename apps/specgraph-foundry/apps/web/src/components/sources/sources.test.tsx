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
const exportDocumentAtoms = vi.fn();
const downloadBase64File = vi.fn();
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
  exportDocumentAtoms: (...args: unknown[]) => exportDocumentAtoms(...args),
}));

vi.mock("@/lib/sources/downloads", () => ({
  downloadBase64File: (...args: unknown[]) => downloadBase64File(...args),
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
  exportDocumentAtoms.mockResolvedValue({
    body: {
      document_id: "doc-1",
      atom_count: 1,
      text: { filename: "atoms-doc1.txt", media_type: "text/plain", byte_length: 4, base64: "dGV4dA==" },
      pdf: { filename: "atoms-doc1.pdf", media_type: "application/pdf", byte_length: 4, base64: "cGRmZA==" },
    },
  });
  downloadBase64File.mockReturnValue(true);
});

describe("source workspace", () => {
  it("renders a premium bounded source library and activity from real API data", async () => {
    renderSource(<SourceWorkspace projectId="project-1" />);
    expect(await screen.findByRole("heading", { name: "Your original documents, never altered" })).toBeInTheDocument();
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

  it("explains a genuine zero-atom extraction result instead of leaving it ambiguous", async () => {
    pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED", result: { document_id: "doc-1", atom_count: 0 } } } });

    renderSource(<DocumentInspector projectId="project-1" documentId="doc-1" />);
    expect(await screen.findByRole("heading", { name: "Spec" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Extract atoms/ }));

    expect(await screen.findByText(/found 0 atoms/)).toBeInTheDocument();
  });

  it("reports the real atom count on a successful extraction", async () => {
    pollOperation.mockResolvedValue({ body: { operation: { state: "SUCCEEDED", result: { document_id: "doc-1", atom_count: 3 } } } });

    renderSource(<DocumentInspector projectId="project-1" documentId="doc-1" />);
    expect(await screen.findByRole("heading", { name: "Spec" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Extract atoms/ }));

    expect(await screen.findByText("Extraction complete — found 3 atoms.")).toBeInTheDocument();
  });

  it("downloads the extracted atoms as a real text file on the extraction screen", async () => {
    renderSource(<DocumentInspector projectId="project-1" documentId="doc-1" />);
    expect(await screen.findByRole("heading", { name: "Spec" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Download atoms (.txt)" }));

    expect(exportDocumentAtoms).toHaveBeenCalledWith(expect.anything(), "doc-1");
    expect(downloadBase64File).toHaveBeenCalledWith("atoms-doc1.txt", "text/plain", "dGV4dA==");
  });

  it("downloads the extracted atoms as a real PDF file on the extraction screen", async () => {
    renderSource(<DocumentInspector projectId="project-1" documentId="doc-1" />);
    expect(await screen.findByRole("heading", { name: "Spec" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Download atoms (.pdf)" }));

    expect(downloadBase64File).toHaveBeenCalledWith("atoms-doc1.pdf", "application/pdf", "cGRmZA==");
  });

  it("surfaces an alert instead of doing nothing when the atoms download fails", async () => {
    exportDocumentAtoms.mockRejectedValue(new Error("network unavailable"));

    renderSource(<DocumentInspector projectId="project-1" documentId="doc-1" />);
    expect(await screen.findByRole("heading", { name: "Spec" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Download atoms (.txt)" }));

    expect(await screen.findByText("Download unavailable")).toBeInTheDocument();
  });

  it("surfaces a visible error instead of doing nothing when the extraction poll fails", async () => {
    // Regression test: pollOperation used to time out at 60s while the
    // scheduled worker runs every ~2 minutes, and this handler had no
    // error handling at all - clicking "Extract atoms" would silently do
    // nothing from the user's perspective (an unhandled rejection).
    const { RequestTimeoutError } = await import("@/lib/api/errors");
    pollOperation.mockRejectedValue(new RequestTimeoutError("Operation polling timed out"));

    renderSource(<DocumentInspector projectId="project-1" documentId="doc-1" />);
    expect(await screen.findByRole("heading", { name: "Spec" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Extract atoms/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Extraction did not complete.");
  });

  it("clears the extraction banner and atom pagination when navigating to a different document", async () => {
    // Regression test: the real page route (sources/[documentId]/page.tsx)
    // passes key={documentId} specifically because Next.js App Router
    // reuses this client component across navigations between different
    // documentId values under the same route pattern, rather than
    // remounting it - without that key, extraction-status/atom-pagination
    // state would carry over from whichever document was viewed
    // previously. This test renders with the same key prop the real page
    // uses, so it actually exercises that remount rather than just
    // swapping props on an already-mounted instance.
    getDocument.mockImplementation((_client: unknown, documentId: string) =>
      Promise.resolve({
        body: documentId === "doc-2"
          ? { id: "doc-2", title: "Other Doc", media_type: "text/markdown", content: "second document", content_sha256: "c".repeat(64) }
          : { id: "doc-1", title: "Spec", media_type: "text/markdown", content: "<b>plain text only</b>", content_sha256: "b".repeat(64) },
      }),
    );

    const { rerender } = renderSource(<DocumentInspector key="doc-1" projectId="project-1" documentId="doc-1" />);
    expect(await screen.findByRole("heading", { name: "Spec" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Extract atoms/ }));
    expect(await screen.findByText("Extraction SUCCEEDED.")).toBeInTheDocument();

    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    rerender(
      <QueryClientProvider client={client}>
        <DocumentInspector key="doc-2" projectId="project-1" documentId="doc-2" />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("heading", { name: "Other Doc" })).toBeInTheDocument();
    expect(screen.queryByText("Extraction SUCCEEDED.")).not.toBeInTheDocument();
  });

  it("has no serious or critical axe violations", async () => {
    const { container } = renderSource(<SourceWorkspace projectId="project-1" />);
    await screen.findByRole("heading", { name: "Your original documents, never altered" });
    await expectNoSeriousAxeViolations(container);
  });
});
