import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";

export function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="sg-auth-page">
      <Card className="sg-auth-card">
        <h1>{title}</h1>
        {children}
      </Card>
    </main>
  );
}
