"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { Button } from "@/components/ui/button";
import { NavLinks } from "@/components/navigation/nav-links";
import { useNavItems } from "@/components/navigation/use-nav-items";

export function MobileNavigation() {
  const { global, project, engineState, developer } = useNavItems();
  return (
    <Dialog.Root>
      <Dialog.Trigger asChild>
        <Button variant="ghost" type="button">
          Menu
        </Button>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="sg-mobile-overlay" />
        <Dialog.Content className="sg-mobile-panel">
          <div className="sg-mobile-panel-brand" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="24" height="24" fill="none">
              <line x1="16" y1="7" x2="8" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              <line x1="16" y1="7" x2="24" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              <line x1="8" y1="23" x2="24" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              <circle cx="16" cy="7" r="3" fill="currentColor" />
              <circle cx="8" cy="23" r="3" fill="currentColor" />
              <circle cx="24" cy="23" r="3" fill="currentColor" />
            </svg>
          </div>
          <Dialog.Title>Navigation</Dialog.Title>
          <NavLinks items={global} className="sg-pressable sg-mobile-panel-link" />
          {project.length > 0 ? <NavLinks items={project} className="sg-pressable sg-mobile-panel-link" /> : null}
          <NavLinks items={engineState} className="sg-pressable sg-mobile-panel-link" />
          {developer.length > 0 ? <NavLinks items={developer} className="sg-pressable sg-mobile-panel-link" /> : null}
          <Dialog.Close asChild>
            <Button variant="secondary" type="button">
              Close
            </Button>
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
