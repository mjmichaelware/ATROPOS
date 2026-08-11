"use client";

import { Slot } from "@radix-ui/react-slot";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  asChild?: boolean;
  loading?: boolean;
  variant?: "primary" | "secondary" | "ghost" | "quiet" | "danger" | "verified";
  children: ReactNode;
};

export function Button({
  asChild = false,
  loading = false,
  variant = "primary",
  className,
  disabled,
  children,
  ...props
}: ButtonProps) {
  const Component = asChild ? Slot : "button";
  return (
    <Component
      className={cn("sg-button", `sg-button-${variant}`, className)}
      aria-busy={loading || undefined}
      disabled={!asChild ? disabled || loading : undefined}
      {...props}
    >
      {loading ? "Working..." : children}
    </Component>
  );
}
