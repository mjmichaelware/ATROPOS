"use client";

import type { ReactNode } from "react";
import { QueryProvider } from "./query-provider";
import { ThemeProvider } from "./theme-provider";
import { AppProvider } from "@/lib/contexts/app-context";
import { SessionStateProvider } from "@/lib/contexts/session-state-context";
import { NotificationDisplay } from "@/components/notifications/notification-display";
import { NotificationPoller } from "./notification-poller";

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <QueryProvider>
        <AppProvider>
          {/* §11.0: closing ATROPOS never destroys active work. Session state
              restores open tabs and the active project across restarts. */}
          <SessionStateProvider>
            <NotificationPoller />
            {children}
            <NotificationDisplay />
          </SessionStateProvider>
        </AppProvider>
      </QueryProvider>
    </ThemeProvider>
  );
}
