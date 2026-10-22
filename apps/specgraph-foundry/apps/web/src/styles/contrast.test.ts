import { describe, expect, it } from "vitest";

/**
 * Deterministic WCAG contrast-ratio evidence for real token pairs actually
 * used to render text/UI in this app — not eyeballed. Values below are the
 * literal hex values from tokens.css/themes.css; if a token is retuned,
 * these numbers must be recomputed against this same test rather than
 * adjusted to match a new value blindly.
 */

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace("#", "");
  return [parseInt(clean.slice(0, 2), 16), parseInt(clean.slice(2, 4), 16), parseInt(clean.slice(4, 6), 16)];
}

function linearize(channel: number): number {
  const c = channel / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function relativeLuminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map(linearize);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrastRatio(foreground: string, background: string): number {
  const l1 = relativeLuminance(foreground);
  const l2 = relativeLuminance(background);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

const AA_NORMAL_TEXT = 4.5;
const AA_LARGE_TEXT_OR_UI = 3;

describe("dark theme token contrast (AA normal text, 4.5:1)", () => {
  const canvas = "#07100d";
  const surface = "#0d1915";
  const elevated = "#14231e";

  it.each([
    ["text-primary on canvas", "#edf7f1", canvas],
    ["text-primary on surface", "#edf7f1", surface],
    ["text-primary on elevated", "#edf7f1", elevated],
    ["text-secondary on surface", "#b8c9c0", surface],
    ["text-muted on surface", "#7f9489", surface],
  ])("%s meets 4.5:1", (_name, fg, bg) => {
    expect(contrastRatio(fg, bg)).toBeGreaterThanOrEqual(AA_NORMAL_TEXT);
  });

  it.each([
    ["focus ring on canvas", "#ffd166", canvas],
    ["accent on canvas", "#64d6a4", canvas],
    ["accent-2 on canvas", "#59d7f7", canvas],
    ["success on canvas", "#79e08d", canvas],
    ["warning on canvas", "#ffd166", canvas],
    ["danger on canvas", "#ff6b6b", canvas],
    ["info on canvas", "#79b8ff", canvas],
  ])("%s meets 3:1 (large text / meaningful UI graphic)", (_name, fg, bg) => {
    expect(contrastRatio(fg, bg)).toBeGreaterThanOrEqual(AA_LARGE_TEXT_OR_UI);
  });
});

describe("light theme token contrast (AA normal text, 4.5:1)", () => {
  const canvas = "#f4f1e8";
  const surface = "#fffaf0";
  const elevated = "#ffffff";

  it.each([
    ["text-primary on canvas", "#17211d", canvas],
    ["text-primary on surface", "#17211d", surface],
    ["text-primary on elevated", "#17211d", elevated],
    ["text-secondary on surface", "#405149", surface],
    ["text-muted on surface", "#586760", surface],
    ["text-muted on canvas", "#586760", canvas],
  ])("%s meets 4.5:1", (_name, fg, bg) => {
    expect(contrastRatio(fg, bg)).toBeGreaterThanOrEqual(AA_NORMAL_TEXT);
  });

  it.each([
    ["focus ring on canvas", "#a9440c", canvas],
    ["accent on canvas", "#106b4b", canvas],
  ])("%s meets 3:1 (large text / meaningful UI graphic)", (_name, fg, bg) => {
    expect(contrastRatio(fg, bg)).toBeGreaterThanOrEqual(AA_LARGE_TEXT_OR_UI);
  });
});

describe("high-contrast theme token contrast", () => {
  const canvas = "#000000";
  const surface = "#050505";

  it.each([
    ["text-primary on canvas", "#ffffff", canvas],
    ["text-secondary on surface", "#f5f5f5", surface],
    ["text-muted on surface", "#d6d6d6", surface],
    ["focus on canvas", "#ffff00", canvas],
    ["accent on canvas", "#00ffff", canvas],
  ])("%s meets 4.5:1", (_name, fg, bg) => {
    expect(contrastRatio(fg, bg)).toBeGreaterThanOrEqual(AA_NORMAL_TEXT);
  });
});
