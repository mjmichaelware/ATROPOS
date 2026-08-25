/* SPDX-License-Identifier: AGPL-3.0-only */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * ADD-W-032: the web surface hosts no engine fork.
 *
 * HOE-C02 makes this surface "thin presentation over existing engine status
 * endpoints; no business logic in Web". The strongest form of that rule is a
 * test: if someone imports an engine package into the browser bundle or grows
 * an orchestrator/verifier twin here, the architecture has forked and this
 * file fails on the commit that did it — not at the next audit.
 */

const SRC_ROOT = join(__dirname, '..');

function* walk(dir: string): Generator<string> {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) yield* walk(full);
    else if (/\.(ts|tsx)$/.test(entry) && !/\.test\.tsx?$/.test(entry)) yield full;
  }
}

describe('ADD-W-032 web hosts no engine fork', () => {
  const files = [...walk(SRC_ROOT)];

  it('imports nothing from the JVM engine or its packages', () => {
    // The engine lives in src/main/kotlin; none of it can run in a browser.
    // Importing it would mean a copied engine, which is the fork.
    const offenders = files.filter((file) => {
      const text = readFileSync(file, 'utf8');
      return /from\s+['"](kotlin|src\/main\/kotlin|atropos\..*)['"]/.test(text);
    });
    expect(offenders).toEqual([]);
  });

  it('declares no orchestrator / verifier / planner twin', () => {
    // Names mirror the engine's authority roles. Prose mentioning them is
    // fine (SpecGraph labels runs); declarations are not.
    const forbidden = /\b(class|const|function)\s+(DirectorService|DagExecutionService|VerifiedCompletionGate|AutonomousOrchestrator|ExecutionPolicyEngine)\b/;
    const offenders = files.filter((file) =>
      forbidden.test(readFileSync(file, 'utf8')),
    );
    expect(offenders).toEqual([]);
  });

  it('keeps every bridge write inside the one client seam', () => {
    // Writes must go through lib/engine writeEngine so attribution and
    // refusal handling stay in exactly one place. Direct fetch() calls to
    // the bridge URL from components would be a second transport.
    const seamPattern = /fetch\(\s*[`'"]?\$\{engineBaseUrl/;
    const offenders = files.filter((file) => {
      if (file.includes(`${'lib'}${'/'}engine`)) return false;
      return seamPattern.test(readFileSync(file, 'utf8'));
    });
    expect(offenders).toEqual([]);
  });
});
