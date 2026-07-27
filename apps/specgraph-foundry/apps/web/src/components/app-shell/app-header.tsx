"use client";

import { Button } from "@/components/ui/button";
import Link from "next/link";
import { SignOutButton } from "@/components/auth/sign-out-button";
import { NavLinks } from "@/components/navigation/nav-links";
import { globalRoutes } from "@/components/navigation/routes";
import { useNavItems } from "@/components/navigation/use-nav-items";

export function AppHeader({ userEmail }: { userEmail?: string }) {
  const { global } = useNavItems();
  const items = userEmail ? global : global.filter((item) => item.id === "projects");
  return (
    <header className="sg-header">
      <Link className="sg-brand" href={globalRoutes.home}>
        <span className="sg-brand-mark" aria-hidden="true" />
        <span>SpecGraph Foundry</span>
      </Link>
      <nav aria-label="Primary" className="sg-header-nav">
        <NavLinks items={items} />
      </nav>
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
