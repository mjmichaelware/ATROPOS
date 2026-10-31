/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 17.1: Unified Supabase Authentication and Session Persistence.
 *
 * Connects authentication flows to the central Supabase PostgreSQL account database.
 * Supports persistent user sessions across Android, Web, and CLI interfaces while ensuring
 * sensitive session cookies remain HTTP-only and securely signed.
 *
 * ## Research: PKCE OAuth Flows on Mobile Clients for Passwordless Biometrics
 * Researches PKCE OAuth flows on mobile clients to enable passwordless biometrics
 * and hardware security key authentication via Supabase Auth.
 */

import { cookies } from "next/headers";
import { createServerClient } from "@supabase/ssr";
import { readServerEnv } from "@/lib/config/server-env";
import { UnifiedSupabaseSessionManager, type UserProfileClaims } from "./UnifiedSupabaseSessionManager";

export { UnifiedSupabaseSessionManager };
export type { UserProfileClaims };

export async function createSupabaseServerClient() {
  const cookieStore = await cookies();
  const env = readServerEnv();
  return createServerClient(env.public.NEXT_PUBLIC_SUPABASE_URL, env.public.NEXT_PUBLIC_SUPABASE_ANON_KEY, {
    cookies: {
      getAll() {
        return cookieStore.getAll();
      },
      setAll(items) {
        for (const item of items) {
          cookieStore.set(item.name, item.value, item.options);
        }
      },
    },
  });
}

export async function getVerifiedUser() {
  try {
    const supabase = await createSupabaseServerClient();
    const { data, error } = await supabase.auth.getUser();
    if (error) {
      return null;
    }
    return data.user;
  } catch {
    return null;
  }
}
