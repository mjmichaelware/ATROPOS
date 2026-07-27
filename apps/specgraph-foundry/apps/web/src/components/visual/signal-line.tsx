export function SignalLine({ active = false }: { active?: boolean }) {
  return <span className="sg-signal-line" data-active={active || undefined} aria-hidden="true" />;
}
