import { formatLineRange, type RangeLike } from "@/lib/sources/ranges";

export function LineRange({ range }: { range: RangeLike }) {
  return <code className="sg-range">{formatLineRange(range)}</code>;
}
