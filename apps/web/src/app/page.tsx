import type { Route } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { getVerifiedUser } from "@/lib/auth/server";
import { redirect } from "next/navigation";

async function getReadiness() {
  const base = process.env.NEXT_PUBLIC_SPECGRAPH_API_URL ?? "http://127.0.0.1:8787";
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 1500);
  try {
    const response = await fetch(new URL("/health/ready", base), {
      signal: controller.signal,
      cache: "no-store",
    });
    return response.ok ? "Ready" : "Unavailable";
  } catch {
    return "Unavailable";
  } finally {
    clearTimeout(timeout);
  }
}

export default async function Home() {
  const user = await getVerifiedUser();
  if (user) {
    redirect("/projects" as Route);
  }
  const readiness = await getReadiness();
  return (
    <div className="sg-hero">
      <section id="foundation" aria-labelledby="foundation-title">
        <StatusBadge tone={readiness === "Ready" ? "success" : "warning"} label={`Backend ${readiness}`} />
        <h1 id="foundation-title">Authority-first delivery starts here.</h1>
        <p>
          Sign in to manage projects, inspect readiness, and use the real command center backed by the SpecGraph API.
        </p>
        <Button asChild>
          <Link href={"/auth/sign-in" as Route}>Sign in</Link>
        </Button>
      </section>

      <section id="capabilities" className="sg-grid" aria-label="Foundation capabilities">
        <Card>
          <h2>Generated contract</h2>
          <p>Types are generated from the OpenAPI 3.1 authority. Frontend code does not duplicate backend domain law.</p>
        </Card>
        <Card>
          <h2>Session boundary</h2>
          <p id="auth">Supabase SSR infrastructure is present without sign-in screens or service-role exposure.</p>
        </Card>
        <Card>
          <h2>Operation-ready</h2>
          <p>Client helpers understand request IDs, pagination headers, ETags, idempotency, and 202 operation polling.</p>
        </Card>
      </section>
    </div>
  );
}
