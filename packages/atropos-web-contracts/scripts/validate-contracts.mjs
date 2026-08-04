/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Checks this package's copy of the vocabularies against the engine's own
 * Kotlin sources.
 *
 * HOE-F01 requires identical status vocabulary across surfaces, and the cheapest
 * way for that to rot is for one side to add a term. This reads the engine's
 * source of truth directly rather than a generated artifact, so the check fails
 * on the commit that introduces the drift rather than at the next release.
 *
 * It parses source text rather than running the JVM on purpose: the check has to
 * be cheap enough to run in a pre-commit path, and a validator that needs a
 * Gradle build is a validator nobody runs.
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { STATUS_TERMS, COMPLETION_TERMS } from '../src/index.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../../..');

const read = (relative) => {
  try {
    return readFileSync(resolve(repoRoot, relative), 'utf8');
  } catch (error) {
    console.error(`CONTRACTS_VALIDATE_FAIL cannot read ${relative}: ${error.message}`);
    process.exit(1);
  }
};

const between = (source, startMarker, endMarker) => {
  const start = source.indexOf(startMarker);
  if (start < 0) return '';
  const end = source.indexOf(endMarker, start + startMarker.length);
  return source.slice(start, end < 0 ? source.length : end);
};

const quoted = (block) => [...block.matchAll(/"([a-z-]+)"/g)].map((m) => m[1]);

const failures = [];

const vocabularySource = read('src/main/kotlin/atropos/cli/ui/design/HoeStatusVocabulary.kt');
const engineStatus = quoted(between(vocabularySource, 'val CANONICAL_TERMS', ')'));
if (engineStatus.join(',') !== STATUS_TERMS.join(',')) {
  failures.push(`status terms differ:\n  engine   = ${engineStatus}\n  contracts= ${STATUS_TERMS}`);
}

const completionSource = read('src/main/kotlin/atropos/cli/ui/design/CompletionVocabulary.kt');
const engineCompletion = COMPLETION_TERMS.filter((term) =>
  completionSource.includes(`"${term}"`),
);
if (engineCompletion.length !== COMPLETION_TERMS.length) {
  const missing = COMPLETION_TERMS.filter((t) => !engineCompletion.includes(t));
  failures.push(`completion terms missing from the engine: ${missing}`);
}

if (failures.length > 0) {
  console.error('CONTRACTS_VALIDATE_FAIL');
  failures.forEach((f) => console.error(`  ${f}`));
  process.exit(1);
}

console.log(`CONTRACTS_VALIDATE_OK status=${STATUS_TERMS.length} completion=${COMPLETION_TERMS.length}`);
