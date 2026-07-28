import { AuthCard } from "@/components/auth/auth-card";
import { authErrorMessage } from "@/lib/auth/redirects";

export default async function AuthErrorPage({ searchParams }: { searchParams: Promise<{ reason?: string }> }) {
  const params = await searchParams;
  return (
    <AuthCard title="Authentication error">
      <p>{authErrorMessage(params.reason)}</p>
      <a href="/auth/sign-in">Return to sign in</a>
    </AuthCard>
  );
}
