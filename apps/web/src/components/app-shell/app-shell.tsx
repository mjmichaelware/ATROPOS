import type { ReactNode } from "react";
import { RouteAccent } from "@/components/navigation/route-accent";
import { EngineStatusBanner } from "@/components/atropos/engine-status-banner";
import { RecoveryRibbon } from "@/components/atropos/recovery-ribbon";
import { WelcomePanel } from "@/components/welcome/welcome-panel";
import { AppHeader } from "./app-header";
import { AppSidebar } from "./app-sidebar";
import { MobileNavigation } from "./mobile-navigation";
import { SkipLink } from "./skip-link";

export function AppShell({ children, userEmail }: { children: ReactNode; userEmail?: string }) {
  return (
    <>
      <SkipLink />
      <RouteAccent />
      <div className="sg-shell-ambient" aria-hidden="true">
        <span className="sg-splash-blob sg-shell-blob-a" />
        <span className="sg-splash-blob sg-shell-blob-b" />
      </div>
      <div className="sg-shell">
        <AppHeader userEmail={userEmail} />
        <div className="sg-mobile-nav">
          <MobileNavigation />
        </div>
        <div className="sg-shell-grid">
          <AppSidebar />
          <main id="main-content" tabIndex={-1} className="sg-main">
            <EngineStatusBanner />
            <RecoveryRibbon />
            {/* SUP.UX.FREE-PROVIDER-WELCOME: shown once per welcome content,
                keyed on its hash rather than a seen flag. */}
            <WelcomePanel />
            {children}
          </main>
        </div>
      </div>
    </>
  );
}
