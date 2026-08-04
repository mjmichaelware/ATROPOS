import path from "node:path";
import type { NextConfig } from "next";

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "no-referrer" },
  { key: "Permissions-Policy", value: "camera=(), geolocation=(), microphone=()" },
  { key: "Cross-Origin-Resource-Policy", value: "same-origin" },
  {
    key: "Content-Security-Policy",
    value: [
      "default-src 'self'",
      "base-uri 'self'",
      "frame-ancestors 'none'",
      "object-src 'none'",
      "img-src 'self' data:",
      "style-src 'self' 'unsafe-inline'",
      "script-src 'self' 'unsafe-inline'",
      "connect-src 'self' http://127.0.0.1:* http://localhost:* https:",
    ].join("; "),
  },
];

const nextConfig: NextConfig = {
  /**
   * The workspace root, stated rather than inferred.
   *
   * `@atropos/design-tokens` and `@atropos/web-contracts` are linked into
   * `apps/web/node_modules` from `packages/`, which is outside this directory.
   * There is no lockfile above `apps/web`, so Turbopack infers the app
   * directory as the root and refuses to follow those links — the build fails
   * on `globals.css` before it reaches a single page, while `tsc` and Vitest
   * both resolve them fine. Naming the root is the difference between a
   * production build that works and one that only ever worked in dev.
   */
  turbopack: {
    root: path.join(__dirname, "..", ".."),
  },
  poweredByHeader: false,
  reactStrictMode: true,
  typedRoutes: true,
  productionBrowserSourceMaps: false,
  images: {
    remotePatterns: [],
  },
  /**
   * SpecGraph moved from /projects/[projectId]/... to /developer/specgraph/...
   * (§1.3, §12.2). Existing links and bookmarks keep working rather than
   * 404ing. These are the SpecGraph-only sections; /projects/[id]/{work,
   * conversations,files,agents} belong to ATROPOS and are untouched.
   */
  async redirects() {
    const specGraphSections = ["sources", "research", "graph", "handoff", "routing", "executions"];
    return [
      {
        source: "/dev-tools",
        destination: "/developer",
        permanent: false,
      },
      ...specGraphSections.map((section) => ({
        source: `/projects/:projectId/${section}/:rest*`,
        destination: `/developer/specgraph/:projectId/${section}/:rest*`,
        permanent: false,
      })),
      {
        source: "/projects/new",
        destination: "/developer/specgraph/new",
        permanent: false,
      },
    ];
  },

  async headers() {
    return [
      {
        source: "/(.*)",
        headers: securityHeaders,
      },
      {
        source: "/sw.js",
        headers: [
          { key: "Content-Type", value: "application/javascript; charset=utf-8" },
          { key: "Cache-Control", value: "no-cache, no-store, must-revalidate" },
          { key: "Content-Security-Policy", value: "default-src 'self'" },
        ],
      },
    ];
  },
};

export default nextConfig;
