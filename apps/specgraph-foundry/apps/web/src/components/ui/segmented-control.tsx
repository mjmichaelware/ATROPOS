import { Button } from "./button";

export function SegmentedControl<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: Array<{ value: T; label: string }>;
  onChange: (value: T) => void;
}) {
  return (
    <div className="sg-segmented" role="group" aria-label={label}>
      {options.map((option) => (
        <Button key={option.value} type="button" variant={option.value === value ? "verified" : "quiet"} aria-pressed={option.value === value} onClick={() => onChange(option.value)}>
          {option.label}
        </Button>
      ))}
    </div>
  );
}
