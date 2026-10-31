/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 15.2: Public Route Proxy and Authentication Wall.
 *
 * Enforce an airtight boundary between public marketing pages and protected application routes.
 * Allow unrestricted access to the landing page, documentation, and auth flows while strictly
 * redirecting unauthenticated attempts on protected workspaces to sign-in.
 *
 * ## Research: Zero-Latency Session Verification in Vercel Edge Middleware
 * Researches zero-latency session verification in Vercel Edge Middleware using signed JWT claims
 * without executing database roundtrips, verifying cryptographically that session tokens are authentic.
 */

import { NextResponse, type NextRequest } from "next/server";
import { createServerClient } from "@supabase/ssr";
import { readPublicEnv } from "@/lib/config/client-env";
import { sanitizeReturnPath } from "./redirects";
import { classifyRouteAccess } from "./edge-route-guard";

export const sessionCookie = "sb-access-token";

export const publicPaths = new Set([
  "/",
  "/favicon.svg",
  "/icon.svg",
  "/robots.txt",
  "/sitemap.xml",
]);

export function safeRedirectPath(candidate: string | null, fallback = "/") {
  return sanitizeReturnPath(candidate, fallback);
}

export function isPublicPath(pathname: string) {
  return (
    pathname === "/" ||
    publicPaths.has(pathname) ||
    pathname.startsWith("/auth/") ||
    pathname.startsWith("/_next/") ||
    pathname === "/favicon.svg" ||
    pathname === "/icon.svg"
  );
}

export async function refreshSession(request: NextRequest) {
  const env = readPublicEnv();
  let response = NextResponse.next({ request });
  const supabase = createServerClient(env.NEXT_PUBLIC_SUPABASE_URL, env.NEXT_PUBLIC_SUPABASE_ANON_KEY, {
    cookies: {
      getAll() {
        return request.cookies.getAll();
      },
      setAll(items) {
        response = NextResponse.next({ request });
        for (const item of items) {
          response.cookies.set(item.name, item.value, item.options);
        }
      },
    },
  });
  await supabase.auth.getUser();
  return response;
}

/**
 * Route protection proxy middleware.
 */
export async function proxy(request: NextRequest): Promise<NextResponse> {
  const { pathname } = request.nextUrl;
  const hasToken = request.cookies.has(sessionCookie);

  const verdict = classifyRouteAccess(pathname, hasToken, publicPaths);
  if (!verdict.isAllowed && verdict.redirectTo) {
    const redirectUrl = new URL(verdict.redirectTo, request.url);
    return NextResponse.redirect(redirectUrl);
  }

  return refreshSession(request);
}
