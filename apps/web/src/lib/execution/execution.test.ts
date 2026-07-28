import { describe, expect, it } from "vitest";
import { redactReceipt, redactReceipts } from "./receipts";
import { isServerReadyExecutionNode, normalizeNodeStatus, normalizeRunStatus, runStatusTone, stageOf } from "./status";

describe("receipt redaction", () => {
  it("never includes the raw evidence object, only a safe field count", () => {
    const raw = {
      id: "receipt-1",
      run_node_id: "node-1",
      actor_system: "atropos",
      actor_id: "worker-1",
      outcome: "SUCCESS",
      summary: "Implementation complete.",
      evidence_sha256: "abc123",
      validation_status: "PENDING",
      created_at: "2026-01-01T00:00:00Z",
      evidence: { secret_log: "sensitive internal trace", credentials: "should-never-render" },
    };
    const safe = redactReceipt(raw);
    expect(safe).not.toHaveProperty("evidence");
    expect(JSON.stringify(safe)).not.toMatch(/secret_log|credentials|sensitive internal trace/);
    expect(safe.evidenceFieldCount).toBe(2);
    expect(safe.evidenceSha256).toBe("abc123");
  });

  it("redacts a full receipt list", () => {
    const receipts = [
      { id: "r1", evidence: { a: 1 } },
      { id: "r2", evidence: { a: 1, b: 2 } },
    ];
    const safe = redactReceipts(receipts);
    expect(safe).toHaveLength(2);
    expect(safe[1].evidenceFieldCount).toBe(2);
  });

  it("handles a missing evidence object without crashing", () => {
    const safe = redactReceipt({ id: "r1" });
    expect(safe.evidenceFieldCount).toBeUndefined();
  });
});

describe("run and node status normalization", () => {
  it("normalizes only real run statuses", () => {
    expect(normalizeRunStatus("verified")).toBe("VERIFIED");
    expect(normalizeRunStatus("something-else")).toBe("UNKNOWN");
    expect(runStatusTone("REJECTED")).toBe("danger");
    expect(runStatusTone("VERIFIED")).toBe("success");
  });

  it("normalizes only real node statuses", () => {
    expect(normalizeNodeStatus("blocked")).toBe("BLOCKED");
    expect(normalizeNodeStatus("nonsense")).toBe("UNKNOWN");
  });

  it("normalizes only real execution stages", () => {
    expect(stageOf("CONTRACT")).toBe("CONTRACT");
    expect(stageOf("nonsense")).toBe("UNKNOWN");
  });
});

describe("server-canonical execution readiness", () => {
  it("derives readiness only from the server ready_nodes list", () => {
    expect(isServerReadyExecutionNode("node-1", [{ id: "node-1" }])).toBe(true);
    expect(isServerReadyExecutionNode("node-1", [{ id: "node-2" }])).toBe(false);
    expect(isServerReadyExecutionNode("node-1", undefined)).toBe(false);
  });
});
