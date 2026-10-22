import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SourceUploadPanel } from "./source-upload-panel";

const createUploadIntent = vi.fn();
const finalizeUpload = vi.fn();
const uploadToSignedUrl = vi.fn();
const sha256Hex = vi.fn();
const fileToBase64 = vi.fn();
const pollOperation = vi.fn();

vi.mock("@/lib/projects/api", () => ({
  createProjectApiClient: () => ({ createIdempotencyKey: () => "idempotency-key", pollOperation }),
}));

vi.mock("@/lib/sources/api", () => ({
  createUploadIntent: (...args: unknown[]) => createUploadIntent(...args),
  finalizeUpload: (...args: unknown[]) => finalizeUpload(...args),
}));

vi.mock("@/lib/sources/upload", () => ({
  uploadToSignedUrl: (...args: unknown[]) => uploadToSignedUrl(...args),
}));

vi.mock("@/lib/sources/hash", () => ({
  sha256Hex: (...args: unknown[]) => sha256Hex(...args),
  fileToBase64: (...args: unknown[]) => fileToBase64(...args),
}));

beforeEach(() => {
  createUploadIntent.mockResolvedValue({
    body: {
      id: "upload-1",
      signed_upload_url: "https://example.supabase.co/storage/v1/object/upload/sign/source-documents/x?token=t",
      required_upload_headers: {},
    },
  });
  uploadToSignedUrl.mockImplementation(async (_url: string, _file: File, _headers: unknown, onProgress: (p: { percent: number }) => void) => {
    onProgress({ percent: 100 });
  });
  sha256Hex.mockResolvedValue("a".repeat(64));
  fileToBase64.mockResolvedValue("aGVsbG8gd29ybGQ=");
});

async function uploadOneFile(onComplete = vi.fn()) {
  render(<SourceUploadPanel projectId="project-1" onComplete={onComplete} />);
  const file = new File(["hello world"], "source.txt", { type: "text/plain" });
  const input = document.getElementById("source-file-input") as HTMLInputElement;
  await userEvent.upload(input, file);
  return onComplete;
}

describe("SourceUploadPanel", () => {
  it("polls to completion against finalize_source_upload's real async operation shape", async () => {
    // finalize_source_upload is registered in ASYNC_OPERATION_TYPES and is
    // dispatched that way in every real deployment (the worker/operations
    // infrastructure is always configured) - a 202 with a `location` to
    // poll, not a direct 201 body. This is a regression test for that
    // shape: assuming a synchronous response here previously crashed with
    // "TypeError: Cannot read properties of undefined (reading 'state')".
    finalizeUpload.mockResolvedValue({
      body: { operation: { id: "op-1", state: "QUEUED" } },
      location: "/v1/operations/op-1",
    });
    pollOperation.mockResolvedValue({
      body: {
        operation: {
          id: "op-1",
          state: "SUCCEEDED",
          result: { document_id: "doc-1", status: "FINALIZED" },
        },
      },
    });

    const onComplete = await uploadOneFile();

    await waitFor(() => expect(screen.getByText("COMPLETE")).toBeInTheDocument());
    expect(screen.queryByText(/Cannot read properties of undefined/)).not.toBeInTheDocument();
    expect(onComplete).toHaveBeenCalled();
    // The browser sends its own copy of the bytes so the worker can verify
    // them directly instead of reading the object back from Supabase
    // Storage.
    expect(finalizeUpload).toHaveBeenCalledWith(expect.anything(), "upload-1", "idempotency-key", "aGVsbG8gd29ybGQ=");
    expect(pollOperation).toHaveBeenCalledWith("/v1/operations/op-1", expect.objectContaining({ timeoutMs: 240_000 }));
  });

  it("surfaces a FAILED item if the polled operation does not succeed", async () => {
    finalizeUpload.mockResolvedValue({
      body: { operation: { id: "op-1", state: "QUEUED" } },
      location: "/v1/operations/op-1",
    });
    pollOperation.mockResolvedValue({
      body: { operation: { id: "op-1", state: "FAILED", error_code: "UPLOAD_INTEGRITY_MISMATCH" } },
    });

    await uploadOneFile();

    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    expect(screen.getAllByText("FAILED").length).toBeGreaterThan(0);
  });

  it("still handles a direct synchronous finalize response as a fallback", async () => {
    // Only reachable if a deployment somehow lacks the operations/worker
    // infrastructure - finalize falls back to a direct 201 body with no
    // `location` header in that case.
    finalizeUpload.mockResolvedValue({
      body: {
        upload_id: "upload-1",
        status: "FINALIZED",
        document_id: "doc-1",
        document_route: "/v1/documents/doc-1",
        document: { id: "doc-1" },
      },
      location: undefined,
    });

    const onComplete = await uploadOneFile();

    await waitFor(() => expect(screen.getByText("COMPLETE")).toBeInTheDocument());
    expect(onComplete).toHaveBeenCalled();
    expect(pollOperation).not.toHaveBeenCalled();
  });
});
