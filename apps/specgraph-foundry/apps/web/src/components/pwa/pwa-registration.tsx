"use client";

import { useEffect, useRef, useState } from "react";
import { VisuallyHidden } from "@/components/ui/visually-hidden";
import { useOnlineStatus } from "@/lib/graph/connectivity";

const UPDATE_CHECK_INTERVAL_MS = 60_000;

/**
 * Registers the shell-only service worker (production only). Every new
 * deployment's worker activates and takes control immediately (sw.js calls
 * self.skipWaiting() unconditionally on install) and this component reloads
 * the page the instant that happens, so a stale/cached frontend is never
 * left showing — no manual "refresh to update" step required. Also polls
 * for a new worker periodically and on tab focus, since browsers otherwise
 * only check for updates on navigation, which a long-open tab may never do.
 */
export function PwaRegistration() {
  const online = useOnlineStatus();
  const [announcement, setAnnouncement] = useState<string | undefined>();
  const lastOnline = useRef<boolean | undefined>(undefined);
  const reloadingRef = useRef(false);

  useEffect(() => {
    if (process.env.NODE_ENV !== "production") return;
    if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return;

    function reloadOnce() {
      if (reloadingRef.current) return;
      reloadingRef.current = true;
      window.location.reload();
    }

    function checkForUpdate() {
      navigator.serviceWorker.getRegistration().then((registration) => registration?.update());
    }

    function handleVisibilityChange() {
      if (document.visibilityState === "visible") checkForUpdate();
    }

    navigator.serviceWorker.addEventListener("controllerchange", reloadOnce);
    navigator.serviceWorker.register("/sw.js", { updateViaCache: "none" }).catch(() => {
      // Registration failure is nonfatal; no raw exception text is surfaced to the user or a log.
    });

    const updateInterval = setInterval(checkForUpdate, UPDATE_CHECK_INTERVAL_MS);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      navigator.serviceWorker.removeEventListener("controllerchange", reloadOnce);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      clearInterval(updateInterval);
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

  return (
    <VisuallyHidden role="status" aria-live="polite">
      {announcement}
    </VisuallyHidden>
  );
}
