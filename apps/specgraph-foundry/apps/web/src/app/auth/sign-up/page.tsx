import type { Route } from "next";
import { redirect } from "next/navigation";
import { AuthCard } from "@/components/auth/auth-card";
import { SignUpForm } from "@/components/auth/sign-up-form";
import { getVerifiedUser } from "@/lib/auth/server";
import { sanitizeReturnPath } from "@/lib/auth/redirects";

export default async function SignUpPage({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const user = await getVerifiedUser();
  if (user) {
    redirect("/projects" as Route);
  }
  const params = await searchParams;
  return (
    <AuthCard title="Create account">
      <SignUpForm nextPath={sanitizeReturnPath(params.next)} />
      <p>
        Already have an account? <a href="/auth/sign-in">Sign in</a>
      </p>
    </AuthCard>
  );
}
