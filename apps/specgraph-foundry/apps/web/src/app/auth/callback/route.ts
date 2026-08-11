import { NextResponse, type NextRequest } from "next/server";
import { createSupabaseServerClient } from "@/lib/auth/server";
import { sanitizeReturnPath } from "@/lib/auth/redirects";

export async function GET(request: NextRequest) {
  const url = new URL(request.url);
  const code = url.searchParams.get("code");
  const next = sanitizeReturnPath(url.searchParams.get("next"));
  if (!code) {
    return NextResponse.redirect(new URL("/auth/error?reason=callback", request.url));
  }
  const supabase = await createSupabaseServerClient();
  const { error } = await supabase.auth.exchangeCodeForSession(code);
  if (error) {
    return NextResponse.redirect(new URL("/auth/error?reason=callback", request.url));
  }
  return NextResponse.redirect(new URL(next, request.url));
}
