import type { CSSProperties } from "react";

export function MetricOrbit({ value, label }: { value: number; label: string }) {
  const bounded = Math.max(0, Math.min(100, value));
  return (
    <div className="sg-metric-orbit" style={{ "--sg-orbit-value": bounded } as CSSProperties} aria-label={`${label}: ${bounded}%`}>
      <div className="sg-metric-orbit-fill" aria-hidden="true" />
      <strong>{bounded}%</strong>
      <small>{label}</small>
    </div>
  );
}
