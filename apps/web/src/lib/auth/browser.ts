"use client";

import { createBrowserClient } from "@supabase/ssr";
import { readPublicEnv } from "@/lib/config/client-env";

export function createSupabaseBrowserClient() {
  const env = readPublicEnv();
  return createBrowserClient(env.NEXT_PUBLIC_SUPABASE_URL, env.NEXT_PUBLIC_SUPABASE_ANON_KEY);
}
