import { NextResponse, type NextRequest } from "next/server";
import { createServerClient } from "@supabase/ssr";
import { readPublicEnv } from "@/lib/config/client-env";
import { sanitizeReturnPath } from "./redirects";

export function safeRedirectPath(candidate: string | null, fallback = "/") {
  return sanitizeReturnPath(candidate, fallback);
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

export function isPublicPath(pathname: string) {
  return (
    pathname === "/" ||
    pathname.startsWith("/auth/") ||
    pathname.startsWith("/_next/") ||
    pathname === "/favicon.svg" ||
    pathname === "/icon.svg"
  );
}
