import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { isPublicPath, refreshSession, safeRedirectPath } from "@/lib/auth/proxy";
import { readPublicEnv } from "@/lib/config/client-env";
import { createServerClient } from "@supabase/ssr";

export async function proxy(request: NextRequest) {
  const response = await refreshSession(request);
  const pathname = request.nextUrl.pathname;
  if (isPublicPath(pathname)) {
    if (pathname === "/auth/sign-in") {
      const env = readPublicEnv();
      const supabase = createServerClient(env.NEXT_PUBLIC_SUPABASE_URL, env.NEXT_PUBLIC_SUPABASE_ANON_KEY, {
        cookies: {
          getAll: () => request.cookies.getAll(),
          setAll: () => undefined,
        },
      });
      const { data } = await supabase.auth.getUser();
      if (data.user) {
        return NextResponse.redirect(new URL("/projects", request.url));
      }
    }
    return response;
  }
  const env = readPublicEnv();
  const supabase = createServerClient(env.NEXT_PUBLIC_SUPABASE_URL, env.NEXT_PUBLIC_SUPABASE_ANON_KEY, {
    cookies: {
      getAll: () => request.cookies.getAll(),
      setAll: () => undefined,
    },
  });
  const { data } = await supabase.auth.getUser();
  if (!data.user) {
    const next = safeRedirectPath(`${pathname}${request.nextUrl.search}`);
    const url = new URL("/auth/sign-in", request.url);
    url.searchParams.set("next", next);
    return NextResponse.redirect(url);
  }
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.svg|icon.svg).*)"],
};
