/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useOptionalSessionState } from "@/lib/contexts/session-state-context";

export enum SurfaceCategory {
  PRIMARY_SPINE = "PRIMARY_SPINE",
  SYSTEM_METRICS = "SYSTEM_METRICS",
  GOVERNANCE = "GOVERNANCE",
  DEVELOPER_INTERNAL = "DEVELOPER_INTERNAL",
}

export enum ScopeType {
  GLOBAL = "GLOBAL",
  PROJECT_BOUND = "PROJECT_BOUND",
}

export interface SurfaceDestination {
  id: string;
  title: string;
  path: string;
  category: SurfaceCategory;
  requiredScope: ScopeType;
}

export interface NavigationItem {
  id: string;
  label: string;
  href: string;
  active: boolean;
  category: SurfaceCategory;
}

export interface BreadcrumbModel {
  segments: Array<{ label: string; href: string; active?: boolean }>;
}

export const ALL_SURFACE_DESTINATIONS: SurfaceDestination[] = [
  // 10-node HOE-A02 primary spine
  { id: "home", title: "Home", path: "/", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.GLOBAL },
  { id: "projects", title: "Projects", path: "/projects", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.GLOBAL },
  { id: "work", title: "Work", path: "/work", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.PROJECT_BOUND },
  { id: "conversations", title: "Conversations", path: "/conversations", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.PROJECT_BOUND },
  { id: "files", title: "Files", path: "/files", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.PROJECT_BOUND },
  { id: "agents", title: "Agents", path: "/agents", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.PROJECT_BOUND },
  { id: "models", title: "Models", path: "/models", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.GLOBAL },
  { id: "automation", title: "Automation", path: "/automation", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.GLOBAL },
  { id: "history", title: "History", path: "/history", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.GLOBAL },
  { id: "settings", title: "Settings", path: "/settings", category: SurfaceCategory.PRIMARY_SPINE, requiredScope: ScopeType.GLOBAL },

  // System & Telemetry Cluster
  { id: "activity", title: "Activity", path: "/activity", category: SurfaceCategory.SYSTEM_METRICS, requiredScope: ScopeType.GLOBAL },
  { id: "telemetry", title: "Telemetry", path: "/system/telemetry", category: SurfaceCategory.SYSTEM_METRICS, requiredScope: ScopeType.GLOBAL },
  { id: "checkpoints", title: "Checkpoints", path: "/checkpoints", category: SurfaceCategory.SYSTEM_METRICS, requiredScope: ScopeType.GLOBAL },
  { id: "benchmarks", title: "Benchmarks", path: "/benchmarks", category: SurfaceCategory.SYSTEM_METRICS, requiredScope: ScopeType.GLOBAL },
  { id: "storage", title: "Storage", path: "/system/storage", category: SurfaceCategory.SYSTEM_METRICS, requiredScope: ScopeType.GLOBAL },

  // Governance Cluster
  { id: "governance", title: "Governance", path: "/governance", category: SurfaceCategory.GOVERNANCE, requiredScope: ScopeType.GLOBAL },
  { id: "audit", title: "Audit Trail", path: "/governance/audit", category: SurfaceCategory.GOVERNANCE, requiredScope: ScopeType.GLOBAL },
  { id: "approvals", title: "Approvals", path: "/approvals", category: SurfaceCategory.GOVERNANCE, requiredScope: ScopeType.GLOBAL },

  // Developer Container
  { id: "developer", title: "Developer Tools", path: "/developer", category: SurfaceCategory.DEVELOPER_INTERNAL, requiredScope: ScopeType.GLOBAL },
  { id: "specgraph", title: "SpecGraph Overview", path: "/developer/specgraph", category: SurfaceCategory.DEVELOPER_INTERNAL, requiredScope: ScopeType.GLOBAL },
  { id: "specgraph-sources", title: "SpecGraph Sources", path: "/developer/specgraph/sources", category: SurfaceCategory.DEVELOPER_INTERNAL, requiredScope: ScopeType.GLOBAL },
  { id: "specgraph-research", title: "SpecGraph Research", path: "/developer/specgraph/research", category: SurfaceCategory.DEVELOPER_INTERNAL, requiredScope: ScopeType.GLOBAL },
  { id: "specgraph-graph", title: "SpecGraph DAG", path: "/developer/specgraph/graph", category: SurfaceCategory.DEVELOPER_INTERNAL, requiredScope: ScopeType.GLOBAL },
  { id: "specgraph-handoff", title: "SpecGraph Handoff", path: "/developer/specgraph/handoff", category: SurfaceCategory.DEVELOPER_INTERNAL, requiredScope: ScopeType.GLOBAL },
  { id: "specgraph-routing", title: "SpecGraph Routing", path: "/developer/specgraph/routing", category: SurfaceCategory.DEVELOPER_INTERNAL, requiredScope: ScopeType.GLOBAL },
];

export class SpineNavigatorCore {
  static resolveActiveDestination(currentPath: string, activeProjectId?: string | null): SurfaceDestination {
    const cleanPath = currentPath.split("?")[0].replace(/\/+$/, "") || "/";
    
    // Check exact matches
    const exact = ALL_SURFACE_DESTINATIONS.find((dest) => dest.path === cleanPath);
    if (exact) return exact;

    // Check project-bound prefix matches
    if (activeProjectId && cleanPath.startsWith(`/projects/${activeProjectId}`)) {
      const sub = cleanPath.replace(`/projects/${activeProjectId}`, "");
      const match = ALL_SURFACE_DESTINATIONS.find((dest) => dest.path === sub);
      if (match) return match;
    }

    // Check prefix matches
    const prefixMatch = ALL_SURFACE_DESTINATIONS
      .filter((dest) => dest.path !== "/" && cleanPath.startsWith(dest.path))
      .sort((a, b) => b.path.length - a.path.length)[0];

    return prefixMatch ?? ALL_SURFACE_DESTINATIONS[0];
  }

  static renderSpineLinks(currentPath: string = "/"): NavigationItem[] {
    const cleanPath = currentPath.split("?")[0].replace(/\/+$/, "") || "/";
    return ALL_SURFACE_DESTINATIONS
      .filter((dest) => dest.category === SurfaceCategory.PRIMARY_SPINE)
      .map((dest) => ({
        id: dest.id,
        label: dest.title,
        href: dest.path,
        active: cleanPath === dest.path || (dest.path !== "/" && cleanPath.startsWith(dest.path)),
        category: dest.category,
      }));
  }

  static renderBreadcrumbTrail(destination: SurfaceDestination, activeProjectId?: string | null): BreadcrumbModel {
    const segments: Array<{ label: string; href: string; active?: boolean }> = [
      { label: "ATROPOS", href: "/" }
    ];

    if (destination.requiredScope === ScopeType.PROJECT_BOUND && activeProjectId) {
      segments.push({ label: `Project: ${activeProjectId}`, href: `/projects/${activeProjectId}` });
    }

    if (destination.path !== "/") {
      segments.push({ label: destination.title, href: destination.path, active: true });
    }

    return { segments };
  }
}

export const SpineNavigator: FC<{ className?: string }> = ({ className = "" }) => {
  const pathname = usePathname() ?? "/";
  const sessionState = useOptionalSessionState();
  const activeProjectId = sessionState?.session.activeProjectId ?? null;
  const spineLinks = SpineNavigatorCore.renderSpineLinks(pathname);
  const activeDest = SpineNavigatorCore.resolveActiveDestination(pathname, activeProjectId);

  return (
    <nav className={`sg-spine-navigator ${className}`} aria-label="Sovereign Navigation Spine">
      <ul className="sg-nav-list flex flex-col space-y-1">
        {spineLinks.map((item) => {
          const isProjectBound = ALL_SURFACE_DESTINATIONS.find((d) => d.id === item.id)?.requiredScope === ScopeType.PROJECT_BOUND;
          const targetHref = isProjectBound && activeProjectId ? `/projects/${activeProjectId}${item.href}` : item.href;

          return (
            <li key={item.id} className="sg-nav-item">
              <Link
                href={targetHref}
                className={`sg-nav-link block px-3 py-2 rounded-md text-sm transition-colors ${
                  item.active ? "bg-sg-surface-elevated font-semibold text-emerald-400" : "text-slate-300 hover:bg-sg-surface"
                }`}
                aria-current={item.active ? "page" : undefined}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
      {activeDest.requiredScope === ScopeType.PROJECT_BOUND && !activeProjectId && (
        <div className="sg-scope-notice mt-4 p-3 bg-amber-950/40 border border-amber-800/50 rounded text-xs text-amber-200">
          <span>No project loaded. Surface is operating in global workspace mode.</span>
        </div>
      )}
    </nav>
  );
};
