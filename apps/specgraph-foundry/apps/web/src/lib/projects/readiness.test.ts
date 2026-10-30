import { describe, expect, it } from "vitest";
import { READINESS_LABELS, readinessLabel, readinessNextAction, readinessTone } from "./readiness";

describe("readiness mapping", () => {
  it("covers every backend readiness state", () => {
    for (const state of Object.keys(READINESS_LABELS)) {
      expect(readinessLabel(state)).not.toBe("Unknown readiness");
      expect(readinessNextAction(state)).not.toBe("Review project state before continuing.");
    }
  });

  it("handles future states safely", () => {
    expect(readinessLabel("FUTURE")).toBe("Unknown readiness");
    expect(readinessTone("FUTURE")).toBe("neutral");
  });
});
