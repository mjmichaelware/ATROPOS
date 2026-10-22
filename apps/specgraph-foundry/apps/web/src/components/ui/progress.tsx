export function Progress({ value, label }: { value?: number; label: string }) {
  const normalized = value === undefined ? undefined : Math.max(0, Math.min(100, value));
  return (
    <div className="sg-progress" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={normalized}>
      <span style={{ inlineSize: `${normalized ?? 34}%` }} data-indeterminate={normalized === undefined || undefined} />
    </div>
  );
}
