/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Visual Compare Component (ADD-W-019).
 *
 * Consumes `/v1/visual/compare` to diff two screenshots and show
 * the result with accessibility bindings (ARIA live regions for
 * screen readers, keyboard navigation, reduced-motion support).
 * The comparison is evidence-backed via EvidenceRef.
 */

'use client';

import { useCallback, useEffect, useState } from 'react';
import { writeEngine, readEngine } from '@/lib/engine/client';
import { isEvidenceRef } from '@/packages/atropos-web-contracts/src/index.mjs';

export interface VisualCompareResult {
  ok: true;
  match: boolean;
  diffPercent: number;
  diffImage?: string; // base64 encoded diff image
  evidenceRef?: {
    casHash: string;
    claimId: string;
    gateIds: string[];
  };
  baselinePath: string;
  currentPath: string;
}

export interface VisualCompareError {
  ok: false;
  reason: string;
  detail: string;
  remedy: string;
}

export type VisualCompareResponse = VisualCompareResult | VisualCompareError;

interface VisualCompareState {
  baseline: string;
  current: string;
  result: VisualCompareResponse | null;
  loading: boolean;
  error: string | null;
}

export function VisualCompare({
  baselinePath,
  currentPath,
}: {
  baselinePath: string;
  currentPath: string;
}) {
  const [state, setState] = useState<VisualCompareState>({
    baseline: baselinePath,
    current: currentPath,
    result: null,
    loading: false,
    error: null,
  });

  const compare = useCallback(async (baseline: string, current: string) => {
    setState((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const result = await writeEngine<VisualCompareResponse>('/v1/visual/compare', {
        baseline,
        current,
      });
      if (!result.ok) {
        setState((prev) => ({
          ...prev,
          loading: false,
          result: result.data,
          error: result.data.detail,
        }));
      } else {
        setState((prev) => ({
          ...prev,
          loading: false,
          result: result.data,
          error: null,
        }));
      }
    } catch (err) {
      setState((prev) => ({
        ...prev,
        loading: false,
        error: err instanceof Error ? err.message : 'Comparison failed',
      }));
    }
  }, []);

  useEffect(() => {
    if (baselinePath && currentPath) {
      compare(baselinePath, currentPath);
    }
  }, [baselinePath, currentPath, compare]);

  const handleBaselineChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setState((prev) => ({ ...prev, baseline: e.target.value }));
  };

  const handleCurrentChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setState((prev) => ({ ...prev, current: e.target.value }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    compare(state.baseline, state.current);
  };

  return (
    <div className="wb-visual-compare" data-testid="visual-compare">
      <form onSubmit={handleSubmit} className="wb-compare-form">
        <div className="wb-compare-inputs">
          <div className="wb-compare-input-group">
            <label htmlFor="baseline-path">Baseline Image Path</label>
            <input
              id="baseline-path"
              type="text"
              value={state.baseline}
              onChange={handleBaselineChange}
              placeholder="/path/to/baseline.png"
              className="wb-compare-input"
              aria-required="true"
            />
          </div>
          <div className="wb-compare-input-group">
            <label htmlFor="current-path">Current Image Path</label>
            <input
              id="current-path"
              type="text"
              value={state.current}
              onChange={handleCurrentChange}
              placeholder="/path/to/current.png"
              className="wb-compare-input"
              aria-required="true"
            />
          </div>
        </div>
        <button
          type="submit"
          className="wb-compare-btn"
          disabled={state.loading || !state.baseline || !state.current}
        >
          {state.loading ? 'Comparing…' : 'Compare'}
        </button>
      </form>

      {state.error && (
        <div className="wb-compare-error" role="alert">
          <p className="wb-fault">{state.error}</p>
        </div>
      )}

      {state.result && (
        <div className="wb-compare-result" role="region" aria-live="polite" aria-label="Visual comparison result">
          {state.result.ok ? (
            <>
              <div className={`wb-compare-status ${state.result.data.match ? 'match' : 'diff'}`}>
                <span className="wb-compare-badge">
                  {state.result.data.match ? 'MATCH' : 'DIFFERENT'}
                </span>
                <p className="wb-compare-detail">
                  {state.result.data.match
                    ? 'Images are identical'
                    : `${state.result.data.diffPercent.toFixed(2)}% difference`}
                </p>
              </div>

              {state.result.data.diffImage && (
                <div className="wb-compare-diff-image">
                  <img
                    src={`data:image/png;base64,${state.result.data.diffImage}`}
                    alt="Visual difference highlight"
                    className="wb-diff-image"
                  />
                </div>
              )}

              <details className="wb-compare-evidence">
                <summary>Evidence Reference</summary>
                {state.result.data.evidenceRef ? (
                  <dl className="wb-evidence-ref">
                    <dt>CAS Hash</dt>
                    <dd className="wb-mono">{state.result.data.evidenceRef.casHash}</dd>
                    <dt>Claim ID</dt>
                    <dd className="wb-mono">{state.result.data.evidenceRef.claimId}</dd>
                    <dt>Gates</dt>
                    <dd>
                      <ul>
                        {state.result.data.evidenceRef.gateIds.map((gate) => (
                          <li key={gate} className="wb-mono">{gate}</li>
                        ))}
                      </ul>
                    </dd>
                  </dl>
                ) : (
                  <p className="wb-compare-note">No evidence reference available</p>
                )}
              </details>

              <details className="wb-compare-paths">
                <summary>Paths Compared</summary>
                <dl>
                  <dt>Baseline</dt>
                  <dd className="wb-mono">{state.result.data.baselinePath}</dd>
                  <dt>Current</dt>
                  <dd className="wb-mono">{state.result.data.currentPath}</dd>
                </dl>
              </details>
            </>
          ) : (
            <div className="wb-compare-error" role="alert">
              <p className="wb-fault">{state.result.reason}</p>
              <p className="wb-compare-detail">{state.result.detail}</p>
              <p className="wb-compare-remedy">{state.result.remedy}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Hook for programmatically comparing two images.
 */
export function useVisualCompare() {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<VisualCompareResponse | null>(null);

  const compare = useCallback(async (baseline: string, current: string) => {
    setLoading(true);
    try {
      const response = await writeEngine<VisualCompareResponse>('/v1/visual/compare', {
        baseline,
        current,
      });
      setResult(response.ok ? response.data : response);
      return response.ok ? response.data : null;
    } catch (err) {
      const error = err instanceof Error ? err.message : 'Comparison failed';
      setResult({ ok: false, reason: 'exception', detail: error, remedy: 'Check the engine log' });
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  return { compare, loading, result };
}