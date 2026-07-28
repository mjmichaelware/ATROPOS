"use client";

import type { ReactNode } from "react";
import { QueryProvider } from "./query-provider";
import { ThemeProvider } from "./theme-provider";
import { AppProvider } from "@/lib/contexts/app-context";
import { NotificationDisplay } from "@/components/notifications/notification-display";

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <QueryProvider>
        <AppProvider>
          {children}
          <NotificationDisplay />
        </AppProvider>
      </QueryProvider>
    </ThemeProvider>
  );
}
