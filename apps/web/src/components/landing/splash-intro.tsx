"use client";

import { useEffect, useRef, useState } from "react";
import { usePrefersReducedMotion } from "@/lib/graph/motion";
import { useViewTransition } from "@/components/ui/view-transition";

const AUTO_DISMISS_MS = 5200;

export function SplashIntro() {
  const [dismissed, setDismissed] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reducedMotion = usePrefersReducedMotion();
  const withViewTransition = useViewTransition();

  useEffect(() => {
    timerRef.current = setTimeout(() => withViewTransition(() => setDismissed(true)), reducedMotion ? 900 : AUTO_DISMISS_MS);
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
    // withViewTransition is stable across renders (useViewTransition only
    // depends on the reducedMotion media query, not on component state), so
    // omitting it here is intentional - including it would restart this
    // timer on every media-query-driven re-render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reducedMotion]);

  useEffect(() => {
    if (dismissed) {
      return;
    }
    function skip() {
      withViewTransition(() => setDismissed(true));
    }
    window.addEventListener("keydown", skip);
    window.addEventListener("pointerdown", skip);
    return () => {
      window.removeEventListener("keydown", skip);
      window.removeEventListener("pointerdown", skip);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dismissed]);

  if (dismissed) {
    return null;
  }

  return (
    <div className="sg-splash">
      <div className="sg-data-grid" aria-hidden="true" />
      <div className="sg-splash-ambient" aria-hidden="true">
        <span className="sg-splash-blob sg-splash-blob-a" />
        <span className="sg-splash-blob sg-splash-blob-b" />
      </div>
      <div className="sg-splash-mark" aria-hidden="true">
        <svg className="sg-splash-glyph" viewBox="0 0 64 64" width="72" height="72" fill="none">
          <line className="sg-splash-glyph-edge" x1="32" y1="14" x2="16" y2="46" strokeWidth="2" strokeLinecap="round" />
          <line className="sg-splash-glyph-edge" x1="32" y1="14" x2="48" y2="46" strokeWidth="2" strokeLinecap="round" />
          <line className="sg-splash-glyph-edge" x1="16" y1="46" x2="48" y2="46" strokeWidth="2" strokeLinecap="round" />
          <circle className="sg-splash-glyph-node" cx="32" cy="14" r="6" />
          <circle className="sg-splash-glyph-node" cx="16" cy="46" r="6" />
          <circle className="sg-splash-glyph-node" cx="48" cy="46" r="6" />
        </svg>
        <p className="sg-splash-title">SpecGraph</p>
        <p className="sg-splash-tagline">Source to verified execution.</p>
      </div>
      <button
        type="button"
        className="sg-button sg-button-secondary sg-splash-skip"
        onClick={() => withViewTransition(() => setDismissed(true))}
      >
        Skip intro <span aria-hidden="true">(press Enter)</span>
      </button>
    </div>
  );
}
