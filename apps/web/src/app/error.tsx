"use client";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export default function Error({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <Card role="alert">
      <h1>Application surface unavailable</h1>
      <p>The web shell could not render this view. No private payload details are shown here.</p>
      <Button type="button" onClick={reset}>
        Retry
      </Button>
    </Card>
  );
}
