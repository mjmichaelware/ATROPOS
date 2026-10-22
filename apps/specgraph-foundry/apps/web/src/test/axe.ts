import axe from "axe-core";

/**
 * Runs axe-core against a rendered container and fails on any serious or
 * critical violation. jsdom has no real layout/paint engine, so a small,
 * explicitly documented set of rules that depend on actual rendered color
 * or geometry are disabled here — not because the underlying WCAG concern
 * doesn't matter, but because jsdom cannot evaluate it and would otherwise
 * produce a false positive/negative unrelated to real component code.
 * Real contrast evidence instead comes from the deterministic token-pair
 * test in src/styles/contrast.test.ts, and real rendered-layout evidence
 * comes from the Playwright @axe-core/playwright run in a true browser.
 */
const JSDOM_FALSE_POSITIVE_RULES = [
  "color-contrast", // jsdom does not compute actual rendered/composited color
  "css-orientation-lock", // depends on real media query evaluation of a rendered viewport
];

export type AxeCheckResult = {
  violations: axe.Result[];
  seriousOrCritical: axe.Result[];
};

export async function runAxeCheck(container: Element, options?: axe.RunOptions): Promise<AxeCheckResult> {
  const results = await axe.run(container, {
    rules: Object.fromEntries(JSDOM_FALSE_POSITIVE_RULES.map((id) => [id, { enabled: false }])),
    ...options,
  });
  const seriousOrCritical = results.violations.filter((violation) => violation.impact === "serious" || violation.impact === "critical");
  return { violations: results.violations, seriousOrCritical };
}

function describeViolations(violations: axe.Result[]): string {
  return violations
    .map((violation) => `${violation.id} (${violation.impact}): ${violation.help} — ${violation.nodes.map((node) => node.target.join(" ")).join(", ")}`)
    .join("\n");
}

/**
 * Vitest assertion helper: throws with a readable report if any serious or
 * critical violation is present. Never disables an entire rule merely to
 * obtain a pass — only the two jsdom-incapable rules above are excluded,
 * globally, with the reason stated once here.
 */
export async function expectNoSeriousAxeViolations(container: Element, options?: axe.RunOptions): Promise<void> {
  const { seriousOrCritical } = await runAxeCheck(container, options);
  if (seriousOrCritical.length > 0) {
    throw new Error(`axe found ${seriousOrCritical.length} serious/critical violation(s):\n${describeViolations(seriousOrCritical)}`);
  }
}
