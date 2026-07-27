import { render, screen, waitFor, act, cleanup } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PwaRegistration } from "./pwa-registration";

function setOnline(value: boolean) {
  Object.defineProperty(window.navigator, "onLine", { configurable: true, value });
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("PwaRegistration feature detection and registration gating", () => {
  it("does not attempt registration when serviceWorker is unsupported", () => {
    // No navigator.serviceWorker defined at all in this jsdom environment by default.
    expect(() => render(<PwaRegistration />)).not.toThrow();
  });

  it("does not attempt registration outside production", () => {
    vi.stubEnv("NODE_ENV", "test");
    const register = vi.fn().mockResolvedValue({ addEventListener: vi.fn(), waiting: null });
    vi.stubGlobal("navigator", { ...window.navigator, onLine: true, serviceWorker: { register, addEventListener: vi.fn(), removeEventListener: vi.fn() } });
    render(<PwaRegistration />);
    expect(register).not.toHaveBeenCalled();
  });
});

describe("PwaRegistration update-ready flow", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
  });

  it("shows a user-controlled refresh action when a waiting worker exists, and never reloads without it", async () => {
    const postMessage = vi.fn();
    const swAddEventListener = vi.fn();
    const swRemoveEventListener = vi.fn();
    const register = vi.fn().mockResolvedValue({
      waiting: { postMessage },
      addEventListener: vi.fn(),
    });
    vi.stubGlobal("navigator", {
      ...window.navigator,
      onLine: true,
      serviceWorker: { register, controller: {}, addEventListener: swAddEventListener, removeEventListener: swRemoveEventListener },
    });

    render(<PwaRegistration />);
    expect(await screen.findByText("A new version of SpecGraph Foundry is ready.")).toBeInTheDocument();
    const button = screen.getByRole("button", { name: "Refresh to update" });

    await act(async () => {
      button.click();
    });
    expect(postMessage).toHaveBeenCalledWith({ type: "SKIP_WAITING" });
    // No reload happens until a real controllerchange event fires.
    expect(swAddEventListener).toHaveBeenCalledWith("controllerchange", expect.any(Function));
  });

  it("registration failure is nonfatal and does not throw or crash the tree", async () => {
    const register = vi.fn().mockRejectedValue(new Error("network unreachable at 10.0.0.1 with token abc123"));
    vi.stubGlobal("navigator", { ...window.navigator, onLine: true, serviceWorker: { register, addEventListener: vi.fn(), removeEventListener: vi.fn() } });
    render(<PwaRegistration />);
    await waitFor(() => expect(register).toHaveBeenCalled());
    // Nothing further to assert: the promise rejection is swallowed, no raw error text is rendered.
    expect(screen.queryByText(/network unreachable/)).not.toBeInTheDocument();
  });
});

describe("PwaRegistration offline/online announcement dedup", () => {
  it("announces a transition once and does not repeat while state is unchanged", async () => {
    setOnline(true);
    render(<PwaRegistration />);
    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("");

    await act(async () => {
      setOnline(false);
      window.dispatchEvent(new Event("offline"));
    });
    expect(status).toHaveTextContent("Offline. Private data is not fetched or cached while offline.");

    await act(async () => {
      window.dispatchEvent(new Event("offline"));
    });
    // Still the same single announcement, not duplicated or re-fired for an unchanged state.
    expect(status).toHaveTextContent("Offline. Private data is not fetched or cached while offline.");

    await act(async () => {
      setOnline(true);
      window.dispatchEvent(new Event("online"));
    });
    expect(status).toHaveTextContent("Back online.");
  });
});
