import type { Provider } from "./schemas";

/**
 * Paid-route unlocks must never be confirmable with unknown cost. The
 * contract exposes no numeric price/currency field, so cost is represented
 * honestly by the selected provider's real `cost_class`. Confirmation is
 * blocked until a real provider with a non-empty cost_class is selected.
 */
export function canConfirmPaidUnlock(provider: Provider | undefined): boolean {
  return Boolean(provider && typeof provider.cost_class === "string" && provider.cost_class.trim().length > 0);
}

export function costSummary(provider: Provider | undefined): string {
  if (!provider) {
    return "Unknown cost. Select a provider to see its real cost class before confirming.";
  }
  if (typeof provider.cost_class !== "string" || !provider.cost_class.trim()) {
    return "Unknown cost. This provider does not report a cost class, so unlocking it is blocked.";
  }
  return `Cost class: ${provider.cost_class}`;
}

export function riskWarning(provider: Provider | undefined): string | undefined {
  if (provider && provider.enabled === false) {
    return "This provider is currently disabled by project configuration.";
  }
  if (provider && provider.status && String(provider.status).toUpperCase() !== "READY") {
    return `This provider's last reported status is ${String(provider.status).toUpperCase()}, not READY.`;
  }
  return undefined;
}
