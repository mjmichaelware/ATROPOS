"use client";

import { useEffect, useRef, useState } from "react";
import { VisuallyHidden } from "@/components/ui/visually-hidden";
import { useOnlineStatus } from "@/lib/graph/connectivity";

/**
 * Registers the shell-only service worker (production only) and surfaces
 * two, and only two, real state changes as concise polite announcements:
 * an offline/online transition, and a waiting worker ready to activate on
 * explicit user action. Never force-reloads, never calls skipWaiting on its
 * own — the user always presses "Refresh to update" first.
 */
export function PwaRegistration() {
  const online = useOnlineStatus();
  const [waitingWorker, setWaitingWorker] = useState<ServiceWorker | undefined>();
  const [announcement, setAnnouncement] = useState<string | undefined>();
  const lastOnline = useRef<boolean | undefined>(undefined);
  const registrationRef = useRef<ServiceWorkerRegistration | undefined>(undefined);

  useEffect(() => {
    if (process.env.NODE_ENV !== "production") return;
    if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return;

    let cancelled = false;

    function handleUpdateFound(registration: ServiceWorkerRegistration) {
      const installing = registration.installing;
      if (!installing) return;
      installing.addEventListener("statechange", () => {
        if (installing.state === "installed" && navigator.serviceWorker.controller) {
          setWaitingWorker(installing);
          setAnnouncement("An update is ready. Refresh to update when convenient.");
        }
      });
    }

    navigator.serviceWorker
      .register("/sw.js", { updateViaCache: "none" })
      .then((registration) => {
        if (cancelled) return;
        registrationRef.current = registration;
        if (registration.waiting && navigator.serviceWorker.controller) {
          setWaitingWorker(registration.waiting);
          setAnnouncement("An update is ready. Refresh to update when convenient.");
        }
        registration.addEventListener("updatefound", () => handleUpdateFound(registration));
      })
      .catch(() => {
        // Registration failure is nonfatal; no raw exception text is surfaced to the user or a log.
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (lastOnline.current === undefined) {
      lastOnline.current = online;
      return;
    }
    if (lastOnline.current !== online) {
      lastOnline.current = online;
      setAnnouncement(online ? "Back online." : "Offline. Private data is not fetched or cached while offline.");
    }
  }, [online]);

  function applyUpdate() {
    waitingWorker?.postMessage({ type: "SKIP_WAITING" });
    setWaitingWorker(undefined);
    setAnnouncement("Updating.");
    const onControllerChange = () => {
      window.location.reload();
      navigator.serviceWorker.removeEventListener("controllerchange", onControllerChange);
    };
    navigator.serviceWorker.addEventListener("controllerchange", onControllerChange);
  }

  return (
    <div className="sg-pwa-status">
      <VisuallyHidden role="status" aria-live="polite">
        {announcement}
      </VisuallyHidden>
      {waitingWorker ? (
        <div className="sg-update-banner" role="status">
          <p>A new version of SpecGraph Foundry is ready.</p>
          <button type="button" className="sg-button sg-button-secondary" onClick={applyUpdate}>
            Refresh to update
          </button>
        </div>
      ) : null}
    </div>
  );
}
