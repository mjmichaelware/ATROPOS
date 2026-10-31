import type { NextConfig } from "next";
import path from "path";

/**
 * ATOM 11.1: SpecGraph Foundry Nested Project Root Configuration.
 *
 * Configures the nested Next.js build root so Vercel resolves dependencies, assets,
 * and route manifests strictly within apps/specgraph-foundry/apps/web without traversing
 * to the parent repository root or colliding with the Atropos cockpit web workspace.
 *
 * ## Research: Vercel Monorepo Project Root & Turborepo Filter Caching
 * Researches Vercel Monorepo Project Root settings and Turborepo workspace filter caching
 * (`--filter=@specgraph-foundry/web...`) to optimize isolated sub-package deployments
 * without pulling unnecessary monorepo dependencies.
 */
export const securityHeaders = [
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

export const nextConfig: NextConfig = {
  distDir: ".next",
  outputFileTracingRoot: path.resolve(__dirname),
  poweredByHeader: false,
  reactStrictMode: true,
  typedRoutes: true,
  productionBrowserSourceMaps: false,
  images: {
    remotePatterns: [],
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
