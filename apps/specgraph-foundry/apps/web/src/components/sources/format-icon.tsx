import { formatLabel } from "@/lib/sources/formats";

export function FormatIcon({ mediaType }: { mediaType?: string }) {
  return <span className="sg-format-icon" aria-label={formatLabel(mediaType)}>{formatLabel(mediaType).slice(0, 4)}</span>;
}
