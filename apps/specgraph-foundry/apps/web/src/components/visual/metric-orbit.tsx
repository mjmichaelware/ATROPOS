export function MetricOrbit({ value, label }: { value: number; label: string }) {
  const bounded = Math.max(0, Math.min(100, value));
  return (
    <div className="sg-metric-orbit" aria-label={`${label}: ${bounded}%`}>
      <span style={{ transform: `rotate(${bounded * 3.6}deg)` }} aria-hidden="true" />
      <strong>{bounded}%</strong>
      <small>{label}</small>
    </div>
  );
}
