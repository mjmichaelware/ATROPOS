/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, it, expect, beforeEach } from 'vitest';
import { WasmEngineBridge } from '../wasm-engine';

describe('WasmEngineBridge (WASM-CORE-001)', () => {
  let bridge: WasmEngineBridge;

  beforeEach(() => {
    bridge = new WasmEngineBridge();
  });

  it('initializes and reports initial stats', async () => {
    const initialized = await bridge.initialize();
    expect(initialized).toBe(true);

    const stats = bridge.getStats();
    expect(stats.totalParsedFiles).toBe(0);
    expect(stats.cachedSubtrees).toBe(0);
  });

  it('parses valid Kotlin class source into AST nodes', () => {
    const source = `
      package atropos.core.test
      import java.nio.file.Path

      class SampleTestService {
          fun runTest(): Boolean {
              return true
          }
      }
    `;

    const result = bridge.parse(source, 'kotlin');
    expect(result.hasErrors).toBe(false);
    expect(result.nodeCount).toBeGreaterThan(0);
    expect(result.root.type).toBe('compilation_unit');

    const types = result.root.children.map((c) => c.type);
    expect(types).toContain('directive');
    expect(types).toContain('declaration_type');
  });

  it('evaluates STRICT_500_LOC invariant correctly', () => {
    const validLines = Array(100).fill('val x = 1').join('\n');
    const validAst = bridge.parse(validLines).root;
    const validCheck = bridge.evaluateInvariant(validAst, 'STRICT_500_LOC');
    expect(validCheck.passed).toBe(true);
    expect(validCheck.violations).toHaveLength(0);

    const invalidLines = Array(505).fill('val x = 1').join('\n');
    const invalidAst = bridge.parse(invalidLines).root;
    const invalidCheck = bridge.evaluateInvariant(invalidAst, 'STRICT_500_LOC');
    expect(invalidCheck.passed).toBe(false);
    expect(invalidCheck.violations.length).toBeGreaterThan(0);
    expect(invalidCheck.violations[0]).toContain('File exceeds strict 500 LOC ceiling');
  });

  it('evaluates NO_LOOPBACK_HTTP_4317 invariant and flags deprecated port', () => {
    const safeCode = 'const val PORT = 8787';
    const safeAst = bridge.parse(safeCode).root;
    const safeCheck = bridge.evaluateInvariant(safeAst, 'NO_LOOPBACK_HTTP_4317');
    expect(safeCheck.passed).toBe(true);

    const deprecatedCode = 'val url = "http://127.0.0.1:4317/v1/messages"';
    const deprecatedAst = bridge.parse(deprecatedCode).root;
    const deprecatedCheck = bridge.evaluateInvariant(deprecatedAst, 'NO_LOOPBACK_HTTP_4317');
    expect(deprecatedCheck.passed).toBe(false);
    expect(deprecatedCheck.violations[0]).toContain('Deprecated loopback port 4317');
  });

  it('evaluates NO_HARDCODED_TERMUX_PATHS invariant', () => {
    const hardcoded = 'val home = "/data/data/com.termux/files/home"';
    const ast = bridge.parse(hardcoded).root;
    const check = bridge.evaluateInvariant(ast, 'NO_HARDCODED_TERMUX_PATHS');
    expect(check.passed).toBe(false);
    expect(check.violations[0]).toContain('Hardcoded Termux path found');
  });

  it('clears node cache properly', () => {
    bridge.parse('val a = 1\nval b = 2');
    expect(bridge.getStats().cachedSubtrees).toBeGreaterThan(0);

    bridge.clearCache();
    expect(bridge.getStats().cachedSubtrees).toBe(0);
  });
});
