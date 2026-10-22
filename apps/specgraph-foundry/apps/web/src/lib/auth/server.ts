import { cookies } from "next/headers";
import { createServerClient } from "@supabase/ssr";
import { readServerEnv } from "@/lib/config/server-env";

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
