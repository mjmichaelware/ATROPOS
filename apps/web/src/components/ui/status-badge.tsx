import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

type StatusTone = "neutral" | "success" | "warning" | "danger" | "info";

type StatusBadgeProps = HTMLAttributes<HTMLSpanElement> & {
  tone?: StatusTone;
  label: string;
};

export function StatusBadge({ tone = "neutral", label, className, ...props }: StatusBadgeProps) {
  return (
    <span className={cn("sg-status", `sg-status-${tone}`, className)} {...props}>
      <span aria-hidden="true" className="sg-status-mark" />
      <span>{label}</span>
    </span>
  );
}
