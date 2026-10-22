import type { TextareaHTMLAttributes } from "react";

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  errorId?: string;
  descriptionId?: string;
};

export function Textarea({ className = "", errorId, descriptionId, ...props }: TextareaProps) {
  return (
    <textarea
      className={`sg-input sg-textarea ${className}`.trim()}
      aria-invalid={Boolean(errorId) || undefined}
      aria-describedby={[descriptionId, errorId].filter(Boolean).join(" ") || undefined}
      {...props}
    />
  );
}
