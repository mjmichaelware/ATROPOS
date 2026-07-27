"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { shortHash } from "@/lib/sources/hash";

export function HashDisplay({ value, label = "SHA-256" }: { value?: string; label?: string }) {
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
      <code aria-label={`${label} ${value ?? "unavailable"}`}>{shortHash(value)}</code>
      {value ? (
        <Button type="button" variant="quiet" onClick={copy}>
          {copied ? "Copied" : "Copy"}
        </Button>
      ) : null}
    </span>
  );
}
