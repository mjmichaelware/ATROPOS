import type { Route } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";

export function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="sg-auth-page">
      <div className="sg-landing-ambient" aria-hidden="true">
        <span className="sg-splash-blob sg-splash-blob-a" />
        <span className="sg-splash-blob sg-splash-blob-b" />
      </div>
      <Link href={"/" as Route} className="sg-auth-home-link">
        ← SpecGraph
      </Link>
      <Card className="sg-auth-card">
        <h1>{title}</h1>
        {children}
      </Card>
    </main>
  );
}
