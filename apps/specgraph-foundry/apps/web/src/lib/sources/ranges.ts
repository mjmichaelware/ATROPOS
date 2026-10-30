export type RangeLike = {
  byte_start?: number;
  byte_end?: number;
  line_start?: number;
  line_end?: number;
};

export function formatByteRange(range: RangeLike) {
  if (!Number.isFinite(range.byte_start) || !Number.isFinite(range.byte_end)) {
    return "bytes unavailable";
  }
  return `bytes ${range.byte_start}-${range.byte_end}`;
}

export function formatLineRange(range: RangeLike) {
  if (!Number.isFinite(range.line_start) || !Number.isFinite(range.line_end)) {
    return "lines unavailable";
  }
  return `lines ${range.line_start}-${range.line_end}`;
}

export function clampRange(start: number, end: number, length: number) {
  return {
    start: Math.max(0, Math.min(start, length)),
    end: Math.max(0, Math.min(Math.max(start, end), length)),
  };
}
