import type { InputHTMLAttributes, ReactNode } from "react";
import { Input } from "./input";
import { Label } from "./label";

type FieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "id"> & {
  id: string;
  label: string;
  error?: string;
  description?: string;
};

export function Field({ id, label, error, description, ...input }: FieldProps) {
  const descriptionId = description ? `${id}-description` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  return (
    <div className="sg-field">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} descriptionId={descriptionId} errorId={errorId} {...input} />
      {description ? <p id={descriptionId}>{description}</p> : null}
      {error ? (
        <p id={errorId} className="sg-field-error">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export function FieldGroup({ children }: { children: ReactNode }) {
  return <div className="sg-field-group">{children}</div>;
}
