import type { InputHTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  errorId?: string;
  descriptionId?: string;
};

export function Input({ className, errorId, descriptionId, "aria-invalid": ariaInvalid, ...props }: InputProps) {
  const describedBy = [descriptionId, errorId].filter(Boolean).join(" ") || undefined;
  return (
    <input
      className={cn("sg-input", className)}
      aria-describedby={describedBy}
      aria-invalid={ariaInvalid ?? Boolean(errorId)}
      {...props}
    />
  );
}
