/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * ATOM 15.3: Authenticated Application Shell Transition.
 *
 * Guards every ATROPOS surface.
 * Verifies active user session credentials prior to mounting the primary IDE shell.
 * Upon successful verification, hydrates the user profile, active project bindings,
 * and workspace layout theme into the application context.
 *
 * ## Research: Optimistic UI Hydration Patterns in React Server Components
 * Researches optimistic UI hydration patterns in React Server Components to eliminate
 * layout shift when transitioning from public auth screens into the authenticated workbench.
 */

import { ReactNode } from 'react';
import type { Route } from 'next';
import { redirect } from 'next/navigation';
import { AppShell } from '@/components/app-shell/app-shell';
import { SessionTabBar } from '@/components/layout/session-tab-bar';
import { getVerifiedUser } from '@/lib/auth/server';
import { buildHydratedSession } from './app-shell-hydrator';

export interface AppLayoutProps {
  children: ReactNode;
}

export { SessionTabBar };

export default async function AppLayout({ children }: AppLayoutProps) {
  const user = await getVerifiedUser();
  if (!user) {
    redirect('/auth/sign-in' as Route);
  }

  const hydratedSession = buildHydratedSession(user.id, user.email ?? undefined);

  return (
    <AppShell userEmail={hydratedSession.email}>
      {children}
    </AppShell>
  );
}
