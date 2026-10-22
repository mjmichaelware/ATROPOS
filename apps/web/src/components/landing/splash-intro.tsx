"use client";

import { useEffect, useRef, useState } from "react";
import { usePrefersReducedMotion } from "@/lib/graph/motion";
import { useViewTransition } from "@/components/ui/view-transition";

const DEFAULT_AUTO_DISMISS_MS = 5200;

export interface SplashIntroProps {
  onDismiss?: () => void;
  autoDismissMs?: number;
}

export function SplashIntro({ onDismiss, autoDismissMs }: SplashIntroProps = {}) {
  const [dismissed, setDismissed] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reducedMotion = usePrefersReducedMotion();
  const withViewTransition = useViewTransition();

  const handleDismiss = () => {
    withViewTransition(() => {
      setDismissed(true);
      onDismiss?.();
    });
  };

  useEffect(() => {
    if (reducedMotion) {
      setDismissed(true);
      onDismiss?.();
      return;
    }

    const duration = autoDismissMs ?? DEFAULT_AUTO_DISMISS_MS;
    timerRef.current = setTimeout(handleDismiss, duration);
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reducedMotion, autoDismissMs]);

  useEffect(() => {
    if (dismissed) {
      return;
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape" || e.key === "Enter") {
        e.preventDefault();
        handleDismiss();
      }
    }
    function handlePointerDown() {
      handleDismiss();
    }

    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("pointerdown", handlePointerDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("pointerdown", handlePointerDown);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dismissed]);

  if (dismissed) {
    return null;
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="SpecGraph Introduction"
      className="fixed inset-0 z-50 bg-sg-canvas sg-splash"
    >
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
        onClick={handleDismiss}
      >
        Skip intro <span aria-hidden="true">(press Enter or Esc)</span>
      </button>
    </div>
  );
}
