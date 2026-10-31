import { z } from "zod";

const urlSchema = z
  .string()
  .min(1)
  .max(2048)
  .url()
  .superRefine((value, context) => {
    const url = new URL(value);
    if (url.username || url.password || url.hash) {
      context.addIssue({ code: "custom", message: "URL cannot contain credentials or fragments" });
    }
    const isVercel = process.env.VERCEL === "1";
    const isLoopback = ["localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]"].includes(url.hostname);
    if (isVercel && process.env.NODE_ENV === "production" && url.protocol !== "https:" && !isLoopback) {
      context.addIssue({ code: "custom", message: "production URLs must use HTTPS" });
    }
    if (!["http:", "https:"].includes(url.protocol)) {
      context.addIssue({ code: "custom", message: "URL protocol is unsupported" });
    }
  });

const publicEnvSchema = z.object({
  NEXT_PUBLIC_SPECGRAPH_API_URL: urlSchema,
  NEXT_PUBLIC_SUPABASE_URL: urlSchema,
  NEXT_PUBLIC_SUPABASE_ANON_KEY: z.string().min(8).max(4096),
});

export type PublicEnv = z.infer<typeof publicEnvSchema>;

export const LOCAL_MOCK_ENV: PublicEnv = {
  NEXT_PUBLIC_SPECGRAPH_API_URL: "http://127.0.0.1:8787",
  NEXT_PUBLIC_SUPABASE_URL: "http://127.0.0.1:8000/mock",
  NEXT_PUBLIC_SUPABASE_ANON_KEY: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.mock_local_anon_key_for_offline_dev",
};

// Next.js only inlines NEXT_PUBLIC_* variables into the browser bundle where
// `process.env.NEXT_PUBLIC_X` appears as a literal, static member expression
// in source. Reading through an indirect variable (as the old
// `source = process.env` default parameter did) is not guaranteed to be
// replaced at build time, so each key is listed explicitly here.
function defaultPublicEnvSource(): Record<string, string | undefined> {
  return {
    NEXT_PUBLIC_SPECGRAPH_API_URL: process.env.NEXT_PUBLIC_SPECGRAPH_API_URL,
    NEXT_PUBLIC_SUPABASE_URL: process.env.NEXT_PUBLIC_SUPABASE_URL,
    NEXT_PUBLIC_SUPABASE_ANON_KEY: process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY,
  };
}

export function readPublicEnv(
  source: Record<string, string | undefined> = defaultPublicEnvSource(),
  options: { strict?: boolean } = {}
): PublicEnv {
  const isVercel = process.env.VERCEL === "1";
  const strict = options.strict ?? isVercel;

  const rawSpecGraphUrl = source.NEXT_PUBLIC_SPECGRAPH_API_URL?.trim();
  const rawSupabaseUrl = source.NEXT_PUBLIC_SUPABASE_URL?.trim();
  const rawSupabaseKey = source.NEXT_PUBLIC_SUPABASE_ANON_KEY?.trim();

  // If not on Vercel, inject graceful mock fallbacks for missing local keys
  const specGraphUrl = rawSpecGraphUrl || (!isVercel ? LOCAL_MOCK_ENV.NEXT_PUBLIC_SPECGRAPH_API_URL : undefined);
  const supabaseUrl = rawSupabaseUrl || (!isVercel ? LOCAL_MOCK_ENV.NEXT_PUBLIC_SUPABASE_URL : undefined);
  const supabaseKey = rawSupabaseKey || (!isVercel ? LOCAL_MOCK_ENV.NEXT_PUBLIC_SUPABASE_ANON_KEY : undefined);

  const result = publicEnvSchema.safeParse({
    NEXT_PUBLIC_SPECGRAPH_API_URL: specGraphUrl,
    NEXT_PUBLIC_SUPABASE_URL: supabaseUrl,
    NEXT_PUBLIC_SUPABASE_ANON_KEY: supabaseKey,
  });

  if (!result.success) {
    if (strict) {
      throw new Error("Public web configuration is invalid");
    }
    console.warn(
      "[ATROPOS Web Warning] Public web configuration is incomplete or invalid: " +
        result.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join(", ") +
        ". Cloud features are disabled due to missing keys, using local ATROPOS bridge fallback."
    );
    return LOCAL_MOCK_ENV;
  }

  const api = new URL(result.data.NEXT_PUBLIC_SPECGRAPH_API_URL);
  const supabase = new URL(result.data.NEXT_PUBLIC_SUPABASE_URL);
  if (api.origin === supabase.origin) {
    if (strict) {
      throw new Error("Public web configuration is invalid");
    }
    console.warn(
      "[ATROPOS Web Warning] NEXT_PUBLIC_SPECGRAPH_API_URL and NEXT_PUBLIC_SUPABASE_URL share the same origin. " +
        "Falling back to isolated mock environment for local ATROPOS bridge UI."
    );
    return LOCAL_MOCK_ENV;
  }

  return result.data;
}
