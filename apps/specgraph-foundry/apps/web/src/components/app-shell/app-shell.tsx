import type { ReactNode } from "react";
import { RouteAccent } from "@/components/navigation/route-accent";
import { AppHeader } from "./app-header";
import { AppSidebar } from "./app-sidebar";
import { MobileNavigation } from "./mobile-navigation";
import { SkipLink } from "./skip-link";

export function AppShell({ children, userEmail }: { children: ReactNode; userEmail?: string }) {
  return (
    <>
      <SkipLink />
      <RouteAccent />
      <div className="sg-shell">
        <AppHeader userEmail={userEmail} />
        <div className="sg-mobile-nav">
          <MobileNavigation />
        </div>
        <div className="sg-shell-grid">
          <AppSidebar />
          <main id="main-content" tabIndex={-1} className="sg-main">
            {children}
          </main>
        </div>
      </div>
    </>
  );
}
