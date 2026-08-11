import { ReactNode } from 'react';
import type { Route } from 'next';
import { redirect } from 'next/navigation';
import { AppShell } from '@/components/app-shell/app-shell';
import { getVerifiedUser } from '@/lib/auth/server';

interface AppLayoutProps {
  children: ReactNode;
}

/**
 * Guards every ATROPOS surface.
 *
 * This check previously existed only on the `(application)` group, so the
 * `(app)` routes — Home, Projects, Work, Models, Settings, Developer Tools —
 * were served without authentication. Moving SpecGraph into this group would
 * have inherited that gap and stripped the guard from the SpecGraph
 * workspaces too, so the guard lives here now and covers both.
 */
export default async function AppLayout({ children }: AppLayoutProps) {
  const user = await getVerifiedUser();
  if (!user) {
    redirect('/auth/sign-in' as Route);
  }

  return <AppShell userEmail={user.email ?? undefined}>{children}</AppShell>;
}
