import { describe, expect, it } from "vitest";
import { detectMediaType } from "./formats";
import { validateSourceFile } from "./file-validation";
import { buildProvenanceNodes } from "./provenance";
import { validateSignedUploadUrl } from "./security";
import { canTransition, uploadReducer, type UploadItem } from "./upload-machine";

function file(name: string, type: string, content = "source") {
  return new File([content], name, { type, lastModified: 1 });
}

describe("source security and upload state", () => {
  it("validates source formats and duplicate files without parsing content", () => {
    const markdown = file("requirements.md", "text/markdown");
    expect(detectMediaType(markdown)).toBe("text/markdown");
    expect(validateSourceFile(markdown).ok).toBe(true);
    expect(validateSourceFile(file("image.png", "image/png")).ok).toBe(false);
    expect(validateSourceFile(markdown, [markdown]).reason).toContain("already");
  });

  it("rejects unsafe signed upload origins", () => {
    expect(() => validateSignedUploadUrl("https://example.supabase.co/storage/v1/object/sign/x", "https://example.supabase.co")).not.toThrow();
    expect(() => validateSignedUploadUrl("https://evil.example/storage/v1/object/sign/x", "https://example.supabase.co")).toThrow(/origin/);
    expect(() => validateSignedUploadUrl("javascript:alert(1)", "https://example.supabase.co")).toThrow();
  });

  it("enforces legal upload transitions", () => {
    expect(canTransition("HASHING", "UPLOADING")).toBe(false);
    const item: UploadItem = { id: "1", filename: "a.md", size: 5, phase: "SELECTED", progress: 0 };
    const next = uploadReducer([item], { type: "transition", id: "1", phase: "COMPLETE" });
    expect(next[0].phase).toBe("FAILED");
  });

  it("builds provenance nodes only from real response data", () => {
    const nodes = buildProvenanceNodes({
      provenance: {
        raw_authority: { byte_count: 100, sha256: "a" },
        derivation: {
          adapter_name: "text",
          derived_byte_count: 80,
          locators_preview: [{ ordinal: 1, label: "page 1", derived_line_start: 1, derived_line_end: 4 }],
        },
      },
    });
    expect(nodes.map((node) => node.label)).toEqual(["Raw authority", "text derivation", "page 1"]);
  });
});
