"use client";

import { usePathname } from "next/navigation";
import type { Route } from "next";
import { activeProjectSection, globalRoutes, isActiveGlobalRoute, projectSections } from "./routes";
import { projectIdFromPathname } from "./route-utils";

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
 */
export function useNavItems(): { global: NavItem[]; project: NavItem[]; projectId: string | undefined } {
  const pathname = usePathname() ?? "/";
  const projectId = projectIdFromPathname(pathname);

  const global: NavItem[] = [
    { id: "projects", label: "Projects", href: globalRoutes.projects, active: isActiveGlobalRoute(pathname, globalRoutes.projects) },
    { id: "new-project", label: "New project", href: globalRoutes.newProject, active: pathname === globalRoutes.newProject },
  ];

  const activeSection = projectId ? activeProjectSection(pathname, projectId) : undefined;
  const project: NavItem[] = projectId
    ? projectSections.map((section) => ({
        id: section.id,
        label: section.label,
        href: section.build(projectId),
        active: activeSection?.id === section.id,
      }))
    : [];

  return { global, project, projectId };
}
