import { afterEach, describe, expect, it, vi } from "vitest";
import { runViewTransition, supportsViewTransitions } from "./view-transition";

type FakeViewTransitionDocument = { startViewTransition?: (callback: () => void) => { ready: Promise<void>; finished: Promise<void>; skipTransition: () => void } };

function asFakeDocument(): FakeViewTransitionDocument {
  return document as unknown as FakeViewTransitionDocument;
}

afterEach(() => {
  delete asFakeDocument().startViewTransition;
});

describe("view transition feature detection", () => {
  it("reports unsupported when the platform API is absent", () => {
    expect(supportsViewTransitions()).toBe(false);
  });

  it("reports supported when the platform API is present", () => {
    asFakeDocument().startViewTransition = () => ({ ready: Promise.resolve(), finished: Promise.resolve(), skipTransition: () => {} });
    expect(supportsViewTransitions()).toBe(true);
  });
});

describe("runViewTransition", () => {
  it("applies the update immediately when the API is unsupported", () => {
    const update = vi.fn();
    runViewTransition(update);
    expect(update).toHaveBeenCalledTimes(1);
  });

  it("applies the update immediately when motion is reduced, even if the API exists", () => {
    const startViewTransition = vi.fn(() => ({ ready: Promise.resolve(), finished: Promise.resolve(), skipTransition: () => {} }));
    asFakeDocument().startViewTransition = startViewTransition;
    const update = vi.fn();
    runViewTransition(update, { reducedMotion: true });
    expect(update).toHaveBeenCalledTimes(1);
    expect(startViewTransition).not.toHaveBeenCalled();
  });

  it("routes the update through document.startViewTransition when supported and motion is allowed", () => {
    const startViewTransition = vi.fn((callback: () => void) => {
      callback();
      return { ready: Promise.resolve(), finished: Promise.resolve(), skipTransition: () => {} };
    });
    asFakeDocument().startViewTransition = startViewTransition;
    const update = vi.fn();
    runViewTransition(update, { reducedMotion: false });
    expect(startViewTransition).toHaveBeenCalledTimes(1);
    expect(update).toHaveBeenCalledTimes(1);
  });

  it("falls back to an immediate update if startViewTransition throws", () => {
    asFakeDocument().startViewTransition = () => {
      throw new Error("boom");
    };
    const update = vi.fn();
    runViewTransition(update, { reducedMotion: false });
    expect(update).toHaveBeenCalledTimes(1);
  });
});
