"use client";

import { Button } from "@/components/ui/button";
import Link from "next/link";
import type { ReactNode } from "react";
import { SignOutButton } from "@/components/auth/sign-out-button";
import { NavLinks } from "@/components/navigation/nav-links";
import { globalRoutes } from "@/components/navigation/routes";
import { useNavItems } from "@/components/navigation/use-nav-items";
import { CommandPalette } from "@/components/ui/command-palette";

export function AppHeader({
  userEmail,
  actions,
}: {
  userEmail?: string;
  /** Optional chrome between the palette and the account area. */
  actions?: ReactNode;
}) {
  const { global } = useNavItems();
  const items = userEmail ? global : global.filter((item) => item.id === "projects");
  return (
    <header className="sg-header">
      <Link className="sg-brand" href={globalRoutes.home}>
        <span className="sg-brand-mark" aria-hidden="true" />
        <span className="font-mono font-bold tracking-wider text-cyan-400">ATROPOS SOVEREIGN</span>
      </Link>
      <nav aria-label="Primary" className="sg-header-nav">
        <NavLinks items={items} />
      </nav>
      <div className="flex items-center gap-2">
        <Link
          href={globalRoutes.studio}
          className="px-2.5 py-1 text-xs font-mono font-semibold rounded-lg bg-cyan-500/10 border border-cyan-500/30 text-cyan-400 hover:bg-cyan-500/20 transition-colors"
        >
          Live Studio
        </Link>
        <a
          href="https://specgraph-foundry.vercel.app"
          target="_blank"
          rel="noopener noreferrer"
          className="px-2.5 py-1 text-xs font-mono font-semibold rounded-lg bg-purple-500/10 border border-purple-500/30 text-purple-300 hover:bg-purple-500/20 transition-colors hidden sm:inline-flex items-center gap-1"
        >
          SpecGraph Vercel ↗
        </a>
      </div>
      <CommandPalette />
      {actions}
      {userEmail ? (
        <div className="sg-account">
          <span className="sg-account-email">{userEmail}</span>
          <SignOutButton />
        </div>
      ) : (
        <Button asChild variant="secondary">
          <Link href={globalRoutes.signIn}>Sign in</Link>
        </Button>
      )}
    </header>
  );
}
