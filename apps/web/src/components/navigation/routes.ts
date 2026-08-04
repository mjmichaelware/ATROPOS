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
  /** SpecGraph's project create form, which moved with its directory. */
  newProject: "/developer/specgraph/new" as Route,
  work: "/work" as Route,
  conversations: "/conversations" as Route,
  files: "/files" as Route,
  agents: "/agents" as Route,
  models: "/models" as Route,
  automation: "/automation" as Route,
  history: "/history" as Route,
  settings: "/settings" as Route,
  devTools: "/developer" as Route,
  specGraph: "/developer/specgraph" as Route,
  signIn: "/auth/sign-in" as Route,
};

/**
 * The §2.0 navigation spine.
 *
 * Home, Projects, Models, Automation, History and Settings are first-class.
 * Developer Tools is carried separately because §2.10 hides it by default.
 *
 * These routes already existed as pages but appeared in no navigation, so the
 * only way to reach them was to type the URL. A page that nothing links to is
 * not a shipped surface.
 */
export type SpineItem = { id: string; label: string; href: Route };

export const navigationSpine: SpineItem[] = [
  { id: "home", label: "Home", href: globalRoutes.home },
  { id: "projects", label: "Projects", href: globalRoutes.projects },
  { id: "work", label: "Work", href: globalRoutes.work },
  { id: "conversations", label: "Conversations", href: globalRoutes.conversations },
  { id: "files", label: "Files", href: globalRoutes.files },
  { id: "agents", label: "Agents", href: globalRoutes.agents },
  { id: "models", label: "Models", href: globalRoutes.models },
  { id: "automation", label: "Automation", href: globalRoutes.automation },
  { id: "history", label: "History", href: globalRoutes.history },
  { id: "settings", label: "Settings", href: globalRoutes.settings },
];

/**
 * HOE-A02's spine in the order the atom names it.
 *
 * Held as data so the ordering is assertable. Work, Conversations, Files and
 * Agents existed only as `/projects/[id]/…` pages before this: reachable while
 * a project was open, absent from navigation otherwise, which made four of the
 * ten spine entries unreachable from the shell.
 */
export const HOE_A02_SPINE_ORDER: readonly string[] = [
  "home",
  "projects",
  "work",
  "conversations",
  "files",
  "agents",
  "models",
  "automation",
  "history",
  "settings",
];

/** §2.10: hidden until the operator asks for it. */
export const developerToolsItem: SpineItem = {
  id: "dev-tools",
  label: "Developer Tools",
  href: globalRoutes.devTools,
};

export function projectRoute(projectId: string): Route {
  return `/projects/${projectId}` as Route;
}

/**
 * SpecGraph workspaces live under Developer Tools.
 *
 * They previously occupied `/projects/[projectId]/...`, which collided with
 * the ATROPOS `/projects/[id]/...` tree: two route groups claimed the same
 * path with different slug names, which Next refuses to build.
 */
export function specGraphProjectRoute(projectId: string): Route {
  return `/developer/specgraph/${projectId}` as Route;
}

export function projectSourcesRoute(projectId: string): Route {
  return `${specGraphProjectRoute(projectId)}/sources` as Route;
}

export function projectDocumentRoute(projectId: string, documentId: string): Route {
  return `${specGraphProjectRoute(projectId)}/sources/${documentId}` as Route;
}

export function projectResearchRoute(projectId: string): Route {
  return `${specGraphProjectRoute(projectId)}/research` as Route;
}

export function projectTaskRoute(projectId: string, taskId: string): Route {
  return `${specGraphProjectRoute(projectId)}/research/tasks/${taskId}` as Route;
}

export function projectGraphRoute(projectId: string): Route {
  return `${specGraphProjectRoute(projectId)}/graph` as Route;
}

export function projectHandoffRoute(projectId: string): Route {
  return `${specGraphProjectRoute(projectId)}/handoff` as Route;
}

export function projectExecutionRoute(projectId: string, runId: string): Route {
  return `${specGraphProjectRoute(projectId)}/executions/${runId}` as Route;
}

export function projectRoutingRoute(projectId: string): Route {
  return `${specGraphProjectRoute(projectId)}/routing` as Route;
}

export function projectWorkRoute(projectId: string): Route {
  return `/projects/${projectId}/work` as Route;
}

export function projectConversationsRoute(projectId: string): Route {
  return `/projects/${projectId}/conversations` as Route;
}

export function projectFilesRoute(projectId: string): Route {
  return `/projects/${projectId}/files` as Route;
}

export function projectAgentsRoute(projectId: string): Route {
  return `/projects/${projectId}/agents` as Route;
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

/**
 * SpecGraph's own workspaces.
 *
 * §1.3 and §12.2: SpecGraph is an engine inside ATROPOS, not the application
 * identity. These sections used to *be* the project navigation, which made a
 * compiler subsystem the primary information architecture. They are retained
 * in full and reached through Developer Tools instead.
 */
export const specGraphSections: ProjectSection[] = [
  {
    id: "overview",
    label: "Overview",
    accent: "neutral",
    build: specGraphProjectRoute,
    matches: (pathname, projectId) => pathname === specGraphProjectRoute(projectId),
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
    matches: (pathname, projectId) => startsWithSegment(pathname, projectHandoffRoute(projectId)) || startsWithSegment(pathname, `${specGraphProjectRoute(projectId)}/executions`),
  },
  {
    id: "routing",
    label: "Routing",
    accent: "routing",
    build: projectRoutingRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectRoutingRoute(projectId)),
  },
];

/**
 * The §2.2–2.6 project spine: what a human directs, not what a compiler emits.
 */
export const projectSections: ProjectSection[] = [
  {
    id: "work",
    label: "Work",
    accent: "neutral",
    build: projectWorkRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectWorkRoute(projectId)),
  },
  {
    id: "conversations",
    label: "Conversations",
    accent: "research",
    build: projectConversationsRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectConversationsRoute(projectId)),
  },
  {
    id: "files",
    label: "Files",
    accent: "sources",
    build: projectFilesRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectFilesRoute(projectId)),
  },
  {
    id: "agents",
    label: "Agents",
    accent: "execution",
    build: projectAgentsRoute,
    matches: (pathname, projectId) => startsWithSegment(pathname, projectAgentsRoute(projectId)),
  },
];

export function activeProjectSection(pathname: string, projectId: string): ProjectSection | undefined {
  return [...projectSections, ...specGraphSections].find((section) =>
    section.matches(pathname, projectId)
  );
}

export function isActiveGlobalRoute(pathname: string, route: Route): boolean {
  if (route === globalRoutes.projects) {
    return pathname === globalRoutes.projects || pathname.startsWith("/projects/");
  }
  return pathname === route;
}
