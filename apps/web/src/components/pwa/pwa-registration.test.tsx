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
    const register = vi.fn().mockResolvedValue({ addEventListener: vi.fn(), update: vi.fn() });
    vi.stubGlobal("navigator", { ...window.navigator, onLine: true, serviceWorker: { register, addEventListener: vi.fn(), removeEventListener: vi.fn(), getRegistration: vi.fn() } });
    render(<PwaRegistration />);
    expect(register).not.toHaveBeenCalled();
  });
});

describe("PwaRegistration auto-update flow", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
  });

  it("registers the worker and reloads the page as soon as a new worker takes control", async () => {
    const register = vi.fn().mockResolvedValue({ addEventListener: vi.fn(), update: vi.fn() });
    let controllerChangeHandler: (() => void) | undefined;
    const swAddEventListener = vi.fn((event: string, handler: () => void) => {
      if (event === "controllerchange") controllerChangeHandler = handler;
    });
    const swRemoveEventListener = vi.fn();
    const reload = vi.fn();
    vi.stubGlobal("navigator", {
      ...window.navigator,
      onLine: true,
      serviceWorker: { register, controller: {}, addEventListener: swAddEventListener, removeEventListener: swRemoveEventListener, getRegistration: vi.fn().mockResolvedValue(undefined) },
    });
    vi.stubGlobal("location", { ...window.location, reload });

    render(<PwaRegistration />);
    await waitFor(() => expect(register).toHaveBeenCalledWith("/sw.js", { updateViaCache: "none" }));
    expect(swAddEventListener).toHaveBeenCalledWith("controllerchange", expect.any(Function));

    act(() => controllerChangeHandler?.());
    expect(reload).toHaveBeenCalledTimes(1);

    // A second controllerchange (should not happen, but must never double-reload) is a no-op.
    act(() => controllerChangeHandler?.());
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("registration failure is nonfatal and does not throw or crash the tree", async () => {
    const register = vi.fn().mockRejectedValue(new Error("network unreachable at 10.0.0.1 with token abc123"));
    vi.stubGlobal("navigator", { ...window.navigator, onLine: true, serviceWorker: { register, addEventListener: vi.fn(), removeEventListener: vi.fn(), getRegistration: vi.fn() } });
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
