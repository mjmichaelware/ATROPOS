import type { Route } from "next";

/**
 * Single source of truth for every application route. No component should
 * assemble a `/projects/${id}/...` string itself — it imports a builder
 * from here instead, so a route never drifts out of sync across the
 * header, sidebar, mobile sheet, breadcrumbs, and command-center links.
 */

export const globalRoutes = {
  home: "/" as Route,
  projects: "/projects" as Route,
  newProject: "/projects/new" as Route,
  signIn: "/auth/sign-in" as Route,
};

export function projectRoute(projectId: string): Route {
  return `/projects/${projectId}` as Route;
}

export function projectSourcesRoute(projectId: string): Route {
  return `/projects/${projectId}/sources` as Route;
}

export function projectDocumentRoute(projectId: string, documentId: string): Route {
  return `/projects/${projectId}/sources/${documentId}` as Route;
}

export function projectResearchRoute(projectId: string): Route {
  return `/projects/${projectId}/research` as Route;
}

export function projectTaskRoute(projectId: string, taskId: string): Route {
  return `/projects/${projectId}/research/tasks/${taskId}` as Route;
}

export function projectGraphRoute(projectId: string): Route {
  return `/projects/${projectId}/graph` as Route;
}

export function projectHandoffRoute(projectId: string): Route {
  return `/projects/${projectId}/handoff` as Route;
}

export function projectExecutionRoute(projectId: string, runId: string): Route {
  return `/projects/${projectId}/executions/${runId}` as Route;
}

export function projectRoutingRoute(projectId: string): Route {
  return `/projects/${projectId}/routing` as Route;
}

export type SectionAccent = "neutral" | "sources" | "research" | "planning" | "handoff" | "execution" | "routing";

export type ProjectSection = {
  id: string;
  label: string;
  accent: SectionAccent;
  build: (projectId: string) => Route;
  /** Matches this section's own route and any of its nested detail routes. */
  matches: (pathname: string, projectId: string) => boolean;
};

function startsWithSegment(pathname: string, base: string): boolean {
  return pathname === base || pathname.startsWith(`${base}/`);
}

export const projectSections: ProjectSection[] = [
  {
    id: "overview",
    label: "Overview",
    accent: "neutral",
    build: projectRoute,
    matches: (pathname, projectId) => pathname === projectRoute(projectId),
  },
  {
    id: "sources",
    label: "Sources",
    accent: "sources",
    build: projectSourcesRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectSourcesRoute(projectId)),
  },
  {
    id: "research",
    label: "Research",
    accent: "research",
    build: projectResearchRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectResearchRoute(projectId)),
  },
  {
    id: "graph",
    label: "Graph",
    accent: "planning",
    build: projectGraphRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectGraphRoute(projectId)),
  },
  {
    id: "handoff",
    label: "Handoff",
    accent: "handoff",
    build: projectHandoffRoute,
    // Execution-run detail pages are reached from the Handoff workspace's
    // Runs tab (there is no standalone `/executions` index route), so they
    // count as part of the Handoff section for active-state purposes.
    matches: (pathname, projectId) => startsWithSegment(pathname, projectHandoffRoute(projectId)) || startsWithSegment(pathname, `/projects/${projectId}/executions`),
  },
  {
    id: "routing",
    label: "Routing",
    accent: "routing",
    build: projectRoutingRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectRoutingRoute(projectId)),
  },
];

export function activeProjectSection(pathname: string, projectId: string): ProjectSection | undefined {
  return projectSections.find((section) => section.matches(pathname, projectId));
}

export function isActiveGlobalRoute(pathname: string, route: Route): boolean {
  if (route === globalRoutes.projects) {
    return pathname === globalRoutes.projects || (pathname.startsWith("/projects/") && !pathname.startsWith(globalRoutes.newProject));
  }
  return pathname === route;
}
