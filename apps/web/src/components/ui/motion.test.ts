import { describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { motionTierForWorkload, useManagedAnimation } from "./motion";
import { createRef } from "react";

describe("motionTierForWorkload", () => {
  it("stays at full motion for small, real workloads", () => {
    expect(motionTierForWorkload(0)).toBe("full");
    expect(motionTierForWorkload(60)).toBe("full");
  });

  it("reduces motion for medium workloads", () => {
    expect(motionTierForWorkload(61)).toBe("reduced");
    expect(motionTierForWorkload(500)).toBe("reduced");
  });

  it("goes minimal for large collections so no mass layout animation runs", () => {
    expect(motionTierForWorkload(501)).toBe("minimal");
    expect(motionTierForWorkload(10000)).toBe("minimal");
  });
});

describe("useManagedAnimation", () => {
  it("cancels the previous animation and cleans up on unmount", () => {
    const cancel = vi.fn();
    const animate = vi.fn(() => ({ cancel }) as unknown as Animation);
    const element = document.createElement("div");
    (element as unknown as { animate: typeof animate }).animate = animate;
    const ref = createRef<Element | null>();
    (ref as { current: Element | null }).current = element;

    const factory = vi.fn(() => animate());
    const { rerender, unmount } = renderHook(({ deps }) => useManagedAnimation(ref, factory, deps), {
      initialProps: { deps: [1] },
    });
    expect(factory).toHaveBeenCalledTimes(1);

    rerender({ deps: [2] });
    expect(factory).toHaveBeenCalledTimes(2);
    expect(cancel).toHaveBeenCalledTimes(1);

    unmount();
    expect(cancel).toHaveBeenCalledTimes(2);
  });

  it("does nothing when the element has no animate method", () => {
    const element = document.createElement("div");
    const ref = createRef<Element | null>();
    (ref as { current: Element | null }).current = element;
    const factory = vi.fn();
    renderHook(() => useManagedAnimation(ref, factory, []));
    expect(factory).not.toHaveBeenCalled();
  });
});
