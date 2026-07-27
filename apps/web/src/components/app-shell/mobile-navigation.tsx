"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { Button } from "@/components/ui/button";
import { NavLinks } from "@/components/navigation/nav-links";
import { useNavItems } from "@/components/navigation/use-nav-items";

export function MobileNavigation() {
  const { global, project } = useNavItems();
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
          <Dialog.Title>Navigation</Dialog.Title>
          <NavLinks items={global} className="sg-pressable sg-mobile-panel-link" />
          {project.length > 0 ? <NavLinks items={project} className="sg-pressable sg-mobile-panel-link" /> : null}
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
