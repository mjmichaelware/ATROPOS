"use client";

import { usePathname } from "next/navigation";
import type { Route } from "next";
import {
  activeProjectSection,
  developerToolsItem,
  globalRoutes,
  isActiveGlobalRoute,
  navigationSpine,
  projectSections,
} from "./routes";
import { projectIdFromPathname } from "./route-utils";
import { useOptionalSessionState } from "@/lib/contexts/session-state-context";

export type NavItem = {
  id: string;
  label: string;
  href: Route;
  active: boolean;
};

/**
 * Single computed nav-item list consumed identically by the header, the
 * desktop sidebar, and the mobile sheet, so route identity, label, and
 * active-state can never drift out of sync between surfaces.
 *
 * The list is the §2.0 spine. It previously carried SpecGraph's workspaces
 * (Sources, Research, Graph, Handoff, Routing) as the project navigation,
 * which promoted a compiler subsystem to primary information architecture and
 * left every ATROPOS page reachable only by typing its URL. SpecGraph is now
 * reached through Developer Tools, which §2.10 hides until asked for.
 */
export function useNavItems(): {
  global: NavItem[];
  project: NavItem[];
  developer: NavItem[];
  projectId: string | undefined;
} {
  const pathname = usePathname() ?? "/";
  const projectId = projectIdFromPathname(pathname);
  // Without a provider the safe default applies: Developer Tools stay hidden.
  const session = useOptionalSessionState()?.session;

  const global: NavItem[] = navigationSpine.map((item) => ({
    id: item.id,
    label: item.label,
    href: item.href,
    active: isActiveGlobalRoute(pathname, item.href),
  }));

  const activeSection = projectId ? activeProjectSection(pathname, projectId) : undefined;
  const project: NavItem[] = projectId
    ? projectSections.map((section) => ({
        id: section.id,
        label: section.label,
        href: section.build(projectId),
        active: activeSection?.id === section.id,
      }))
    : [];

  // §2.10: hidden by default. Revealed only by an explicit operator
  // preference, and shown regardless while the operator is already inside it
  // so the surface can never strand them without a way back.
  const developerVisible =
    (session?.developerToolsEnabled ?? false) || pathname.startsWith(globalRoutes.devTools);

  const developer: NavItem[] = developerVisible
    ? [
        {
          id: developerToolsItem.id,
          label: developerToolsItem.label,
          href: developerToolsItem.href,
          active: pathname.startsWith(globalRoutes.devTools),
        },
      ]
    : [];

  return { global, project, developer, projectId };
}
