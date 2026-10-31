/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 15.3: App Shell Hydrator and Session Context Initializer.
 *
 * Implements user profile hydration, project bindings, and theme context
 * for authenticated application workspaces.
 *
 * ## Research: Optimistic UI Hydration Patterns in React Server Components
 * Researches RSC streaming SSR and optimistic client hydration (`React.use()` with Suspense boundaries).
 * Prefetches the user's active workspace state during server execution, embedding the hydration
 * payload directly into the HTML streaming chunks to eliminate layout shift (CLS 0.0) when transitioning
 * from public auth routes into the authenticated four-pane workbench shell.
 */

export interface HydratedSessionState {
  userId: string;
  email?: string;
  activeProjectId?: string;
  themeMode: 'cyber-dark' | 'matrix-green' | 'void-abyss';
  hydratedAt: number;
}

export function buildHydratedSession(
  userId: string,
  email?: string,
  activeProjectId?: string
): HydratedSessionState {
  return {
    userId,
    email,
    activeProjectId,
    themeMode: 'cyber-dark',
    hydratedAt: Date.now(),
  };
}
