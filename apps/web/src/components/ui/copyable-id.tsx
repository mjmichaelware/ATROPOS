"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";

export function CopyableId({ value, label }: { value?: string; label: string }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    if (!value) {
      return;
    }
    await navigator.clipboard?.writeText(value);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  }
  return (
    <span className="sg-hash">
      <span className="sg-micro-label">{label}</span>
      <code aria-label={`${label} ${value ?? "unavailable"}`}>{value ?? "unavailable"}</code>
      {value ? (
        <Button type="button" variant="quiet" onClick={copy}>
          {copied ? "Copied" : "Copy"}
        </Button>
      ) : null}
    </span>
  );
}
