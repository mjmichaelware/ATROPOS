import { flushSync } from "react-dom";
import { usePrefersReducedMotion } from "@/lib/graph/motion";

type ViewTransitionCapableDocument = Document & {
  startViewTransition?: (callback: () => void | Promise<void>) => { ready: Promise<void>; finished: Promise<void>; skipTransition: () => void };
};

export function supportsViewTransitions(): boolean {
  return typeof document !== "undefined" && typeof (document as ViewTransitionCapableDocument).startViewTransition === "function";
}

/**
 * Runs `update` inside a feature-detected View Transition when the browser
 * supports it and motion is not reduced; otherwise applies the update
 * immediately. Never imports React's experimental <ViewTransition> — this
 * is the platform document.startViewTransition API only. The update is
 * flushed synchronously (flushSync) inside the transition callback, which
 * React's own view-transition guidance requires so the "after" DOM snapshot
 * is captured correctly instead of an async/batched update racing the
 * browser's paint.
 */
export function runViewTransition(update: () => void, options?: { reducedMotion?: boolean }): void {
  const reduced = options?.reducedMotion ?? false;
  const doc = typeof document !== "undefined" ? (document as ViewTransitionCapableDocument) : undefined;
  if (reduced || !doc?.startViewTransition) {
    update();
    return;
  }
  try {
    doc.startViewTransition(() => flushSync(update));
  } catch {
    update();
  }
}

export function useViewTransition(): (update: () => void) => void {
  const reducedMotion = usePrefersReducedMotion();
  return (update: () => void) => runViewTransition(update, { reducedMotion });
}
