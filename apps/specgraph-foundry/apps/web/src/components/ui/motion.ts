import { useEffect, useRef } from "react";

export { usePrefersReducedMotion } from "@/lib/graph/motion";

export type MotionTier = "full" | "reduced" | "minimal";

/**
 * Adaptive-performance policy driven by real workload size, never a device
 * name or user-agent check. Callers pass the count of items a motion effect
 * would touch (list entries, graph nodes); this returns how much motion
 * grammar is safe to run.
 */
export function motionTierForWorkload(itemCount: number): MotionTier {
  if (itemCount > 500) return "minimal";
  if (itemCount > 60) return "reduced";
  return "full";
}

/**
 * Runs a Web Animations API animation and guarantees it is cancelled on
 * unmount or when the effect re-runs, so no orphaned Animation objects or
 * DOM references survive a component update. Intended only for
 * coordination CSS keyframes/transitions cannot express (e.g. FLIP-style
 * list reordering); prefer CSS for stable state-driven effects.
 */
export function useManagedAnimation(
  ref: React.RefObject<Element | null>,
  factory: (element: Element) => Animation | undefined,
  deps: unknown[],
): void {
  const animationRef = useRef<Animation | undefined>(undefined);
  useEffect(() => {
    const element = ref.current;
    if (!element || typeof element.animate !== "function") return;
    animationRef.current?.cancel();
    animationRef.current = factory(element);
    return () => {
      animationRef.current?.cancel();
      animationRef.current = undefined;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- deps is an intentional caller-provided dependency list, not a literal
  }, deps);
}
