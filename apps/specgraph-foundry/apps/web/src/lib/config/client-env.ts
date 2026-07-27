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
    if (process.env.NODE_ENV === "production" && url.protocol !== "https:") {
      context.addIssue({ code: "custom", message: "production URLs must use HTTPS" });
    }
    if (process.env.NODE_ENV !== "production" && !["http:", "https:"].includes(url.protocol)) {
      context.addIssue({ code: "custom", message: "URL protocol is unsupported" });
    }
  });

const publicEnvSchema = z.object({
  NEXT_PUBLIC_SPECGRAPH_API_URL: urlSchema,
  NEXT_PUBLIC_SUPABASE_URL: urlSchema,
  NEXT_PUBLIC_SUPABASE_ANON_KEY: z.string().min(8).max(4096),
});

export type PublicEnv = z.infer<typeof publicEnvSchema>;

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

export function readPublicEnv(source: Record<string, string | undefined> = defaultPublicEnvSource()): PublicEnv {
  const result = publicEnvSchema.safeParse({
    NEXT_PUBLIC_SPECGRAPH_API_URL: source.NEXT_PUBLIC_SPECGRAPH_API_URL ?? "http://127.0.0.1:8787",
    NEXT_PUBLIC_SUPABASE_URL: source.NEXT_PUBLIC_SUPABASE_URL,
    NEXT_PUBLIC_SUPABASE_ANON_KEY: source.NEXT_PUBLIC_SUPABASE_ANON_KEY,
  });
  if (!result.success) {
    throw new Error("Public web configuration is invalid");
  }
  const api = new URL(result.data.NEXT_PUBLIC_SPECGRAPH_API_URL);
  const supabase = new URL(result.data.NEXT_PUBLIC_SUPABASE_URL);
  if (api.origin === supabase.origin) {
    throw new Error("Public web configuration is invalid");
  }
  return result.data;
}
