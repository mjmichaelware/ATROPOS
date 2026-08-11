import { describe, expect, it } from "vitest";
import { canConfirmPaidUnlock, costSummary, riskWarning } from "./cost";
import type { Provider } from "./schemas";

describe("paid unlock cost gating", () => {
  it("blocks confirmation when no provider is selected", () => {
    expect(canConfirmPaidUnlock(undefined)).toBe(false);
    expect(costSummary(undefined)).toMatch(/Unknown cost/);
  });

  it("blocks confirmation when the selected provider has no cost_class", () => {
    const provider: Provider = { id: "p1", name: "Provider One" };
    expect(canConfirmPaidUnlock(provider)).toBe(false);
    expect(costSummary(provider)).toMatch(/Unknown cost/);
  });

  it("allows confirmation only once a real cost_class is present", () => {
    const provider: Provider = { id: "p1", name: "Provider One", cost_class: "HIGH" };
    expect(canConfirmPaidUnlock(provider)).toBe(true);
    expect(costSummary(provider)).toBe("Cost class: HIGH");
  });

  it("never fabricates savings or quality claims not in the record", () => {
    const provider: Provider = { id: "p1", cost_class: "LOW" };
    expect(costSummary(provider)).not.toMatch(/save|best|fastest|guaranteed/i);
  });

  it("surfaces a real disabled/non-ready risk warning only when returned", () => {
    expect(riskWarning({ id: "p1", enabled: false })).toMatch(/disabled/);
    expect(riskWarning({ id: "p1", status: "COOLDOWN" })).toMatch(/COOLDOWN/);
    expect(riskWarning({ id: "p1", status: "READY", enabled: true })).toBeUndefined();
  });
});
