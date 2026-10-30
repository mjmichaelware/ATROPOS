import { describe, expect, it } from "vitest";
import { validateConclusion } from "./conclusions";
import { validateEvidence } from "./evidence";
import { countStatuses, gapRows } from "./gaps";
import { leaseRisk } from "./leases";
import { validateEvidenceUrl } from "./security";
import { completionRatio, normalizeDimensionStatus } from "./status";

describe("research contract helpers", () => {
  it("maps dimension states and completion ratios without inventing statuses", () => {
    expect(normalizeDimensionStatus("RESOLVED")).toBe("RESOLVED");
    expect(normalizeDimensionStatus("future")).toBe("UNKNOWN");
    expect(completionRatio({ open_dimensions: 1, resolved_dimensions: 2, not_applicable_dimensions: 1 })).toBe(75);
  });

  it("builds matrix rows only from returned atom dimensions", () => {
    const matrix = {
      atoms: [
        { id: "atom-1", dimensions: { safety: "OPEN", provenance: { status: "RESOLVED" } } },
        { id: "atom-2", dimensions: [{ dimension: "scope", status: "NOT_APPLICABLE" }] },
      ],
    };
    expect(gapRows(matrix).flatMap((row) => row.cells)).toHaveLength(3);
    expect(countStatuses(matrix)).toEqual({ open: 1, resolved: 1, notApplicable: 1, unknown: 0 });
  });

  it("rejects unsafe evidence URLs and validates evidence fields", () => {
    expect(validateEvidenceUrl("javascript:alert(1)")).toContain("HTTPS");
    expect(validateEvidenceUrl("https://user@example.test/source")).toContain("credentials");
    expect(validateEvidence({ source_uri: "https://example.test/spec", source_title: "Spec", excerpt: "Required" })).toEqual({});
  });

  it("requires explicit NOT_APPLICABLE justification and real evidence", () => {
    expect(validateConclusion({ applicability: "NOT_APPLICABLE", conclusion: "no", confidence: 0.8, evidence_ids: ["ev-1"] }).conclusion).toMatch(/Explain/);
    expect(validateConclusion({ applicability: "APPLICABLE", conclusion: "Resolved by evidence.", confidence: 0.8, evidence_ids: [] }).evidence_ids).toMatch(/At least one/);
  });

  it("classifies lease risk from backend expiration", () => {
    expect(leaseRisk({ lease_expires_at: "2026-01-01T00:00:40Z" }, Date.parse("2026-01-01T00:00:00Z"))).toBe("near-expiry");
    expect(leaseRisk({ lease_expires_at: "2026-01-01T00:00:00Z" }, Date.parse("2026-01-01T00:00:01Z"))).toBe("lost");
  });
});
