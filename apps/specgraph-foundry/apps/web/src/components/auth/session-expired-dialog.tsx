"use client";

import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

export function SessionExpiredDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="sg-mobile-overlay" />
        <Dialog.Content className="sg-mobile-panel">
          <Dialog.Title>Session expired</Dialog.Title>
          <p>Your session expired. Sign in again to continue.</p>
          <Button type="button" onClick={() => window.location.assign("/auth/sign-in?reason=expired")}>
            Sign in
          </Button>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
