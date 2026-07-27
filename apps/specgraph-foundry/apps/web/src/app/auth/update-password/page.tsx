import type { Route } from "next";
import { redirect } from "next/navigation";
import { AuthCard } from "@/components/auth/auth-card";
import { UpdatePasswordForm } from "@/components/auth/update-password-form";
import { getVerifiedUser } from "@/lib/auth/server";

export default async function UpdatePasswordPage() {
  const user = await getVerifiedUser();
  if (!user) {
    redirect("/auth/error?reason=recovery" as Route);
  }
  return (
    <AuthCard title="Update password">
      <UpdatePasswordForm />
    </AuthCard>
  );
}
