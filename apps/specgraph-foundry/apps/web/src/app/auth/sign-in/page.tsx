import type { Route } from "next";
import { redirect } from "next/navigation";
import { AuthCard } from "@/components/auth/auth-card";
import { SignInForm } from "@/components/auth/sign-in-form";
import { getVerifiedUser } from "@/lib/auth/server";
import { sanitizeReturnPath } from "@/lib/auth/redirects";

export default async function SignInPage({ searchParams }: { searchParams: Promise<{ next?: string; reason?: string }> }) {
  const user = await getVerifiedUser();
  if (user) {
    redirect("/projects" as Route);
  }
  const params = await searchParams;
  return (
    <AuthCard title="Sign in">
      {params.reason === "expired" ? <p role="status">Your session expired. Sign in again to continue.</p> : null}
      <SignInForm nextPath={sanitizeReturnPath(params.next)} />
    </AuthCard>
  );
}
