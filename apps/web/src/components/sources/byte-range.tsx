import { formatByteRange, type RangeLike } from "@/lib/sources/ranges";

export function ByteRange({ range }: { range: RangeLike }) {
  return <code className="sg-range">{formatByteRange(range)}</code>;
}
