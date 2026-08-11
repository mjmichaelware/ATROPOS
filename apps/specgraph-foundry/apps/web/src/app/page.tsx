import type { Route } from "next";
import { redirect } from "next/navigation";
import { LandingHero } from "@/components/landing/landing-hero";
import { SplashIntro } from "@/components/landing/splash-intro";
import { getVerifiedUser } from "@/lib/auth/server";

export default async function Home() {
  const user = await getVerifiedUser();
  if (user) {
    redirect("/projects" as Route);
  }
  return (
    <>
      <SplashIntro />
      <LandingHero />
    </>
  );
}
