import type { Route } from "next";
import { redirect } from "next/navigation";
import { AppShell } from "@/components/app-shell/app-shell";
import { getVerifiedUser } from "@/lib/auth/server";

export default async function ApplicationLayout({ children }: { children: React.ReactNode }) {
  const user = await getVerifiedUser();
  if (!user) {
    redirect("/auth/sign-in" as Route);
  }
  return <AppShell userEmail={user.email ?? undefined}>{children}</AppShell>;
}
