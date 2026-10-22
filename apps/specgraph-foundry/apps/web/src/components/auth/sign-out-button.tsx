"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { createSupabaseBrowserClient } from "@/lib/auth/browser";
import { resetSessionExpiredFlag } from "@/lib/auth/session";

export function SignOutButton() {
  const queryClient = useQueryClient();
  async function signOut() {
    queryClient.clear();
    resetSessionExpiredFlag();
    await createSupabaseBrowserClient().auth.signOut();
    window.location.assign("/auth/sign-in");
  }
  return (
    <Button type="button" variant="secondary" onClick={signOut}>
      Sign out
    </Button>
  );
}
