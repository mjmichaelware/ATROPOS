import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

type AlertProps = HTMLAttributes<HTMLDivElement> & {
  tone?: "info" | "warning" | "danger" | "success";
  title?: string;
};

export function Alert({ className, tone = "info", title, children, ...props }: AlertProps) {
  return (
    <div role={tone === "danger" ? "alert" : "status"} className={cn("sg-alert", className)} data-tone={tone} {...props}>
      {title ? <strong>{title}</strong> : null}
      {children}
    </div>
  );
}
