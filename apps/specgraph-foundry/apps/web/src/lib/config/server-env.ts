import { z } from "zod";
import { readPublicEnv } from "./client-env";

const serverEnvSchema = z.object({
  SPECGRAPH_WEB_BASE_URL: z.string().url().optional(),
});

export type ServerEnv = z.infer<typeof serverEnvSchema> & {
  public: ReturnType<typeof readPublicEnv>;
};

export function readServerEnv(source: Record<string, string | undefined> = process.env): ServerEnv {
  const publicEnv = readPublicEnv(source);
  const result = serverEnvSchema.safeParse({
    SPECGRAPH_WEB_BASE_URL: source.SPECGRAPH_WEB_BASE_URL ?? "http://127.0.0.1:3000",
  });
  if (!result.success) {
    throw new Error("Server web configuration is invalid");
  }
  return { ...result.data, public: publicEnv };
}

export function assertNoServerSecretsInPublicEnv(source: Record<string, string | undefined>) {
  for (const key of Object.keys(source)) {
    if (key.startsWith("NEXT_PUBLIC_") && /(service|secret|database|password|token)/i.test(key)) {
      throw new Error("Public web configuration is invalid");
    }
  }
}
