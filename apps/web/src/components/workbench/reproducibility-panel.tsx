/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Reproducibility Panel (ADD-W-023).
 *
 * Consumes `/v1/reproducibility` to evaluate or snapshot reproducibility.
 * Shows the reproducibility predicate panel with evidence refs and
 * pass/fail for each predicate. This is the P20-G09 reproducibility
 * requirement surfaced in the UI.
 */

'use client';

import { useCallback, useEffect, useState } from 'react';
import { writeEngine, readEngine } from '@/lib/engine/client';

export interface ReproducibilityPredicate {
  name: string;
  passed: boolean;
  evidenceRef?: {
    casHash: string;
    claimId: string;
    gateIds: string[];
  };
  detail: string;
  expected?: string;
  actual?: string;
}

export interface ReproducibilityResult {
  ok: true;
  reproducible: boolean;
  predicates: ReproducibilityPredicate[];
  snapshotId?: string;
  timestamp: string;
}

export interface ReproducibilityError {
  ok: false;
  reason: string;
  detail: string;
  remedy: string;
}

export type ReproducibilityResponse = ReproducibilityResult | ReproducibilityError;

interface ReproducibilityState {
  files: Record<string, string>; // path -> sha256
  action: 'evaluate' | 'snapshot';
  result: ReproducibilityResponse | null;
  loading: boolean;
  error: string | null;
}

export function ReproducibilityPanel() {
  const [state, setState] = useState<ReproducibilityState>({
    files: {},
    action: 'evaluate',
    result: null,
    loading: false,
    error: null,
  });

  const run = useCallback(async () => {
    setState((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const result = await writeEngine<ReproducibilityResponse>('/v1/reproducibility', {
        action: state.action,
        files: state.action === 'evaluate' ? state.files : undefined,
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
        error: err instanceof Error ? err.message : 'Reproducibility check failed',
      }));
    }
  }, [state.files, state.action]);

  const addFile = useCallback((path: string, sha256: string) => {
    setState((prev) => ({
      ...prev,
      files: { ...prev.files, [path]: sha256 },
    }));
  }, []);

  const removeFile = useCallback((path: string) => {
    setState((prev) => {
      const { [path]: removed, ...rest } = prev.files;
      return { ...prev, files: rest };
    });
  }, []);

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const path = e.target.dataset.path ?? '';
    if (path) {
      addFile(path, e.target.value);
    }
  };

  const handleNewFile = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const path = formData.get('path') as string;
    const sha256 = formData.get('sha256') as string;
    if (path && sha256) {
      addFile(path, sha256);
      e.currentTarget.reset();
    }
  };

  if (!state.result && !state.loading) {
    return (
      <div className="wb-reproducibility" data-testid="reproducibility-panel">
        <form onSubmit={handleNewFile} className="wb-repro-add-file">
          <h3>Add File for Reproducibility Check</h3>
          <div className="wb-repro-file-input">
            <input
              type="text"
              name="path"
              placeholder="path/to/file"
              required
              aria-label="File path"
            />
            <input
              type="text"
              name="sha256"
              placeholder="sha256 hash (64 hex chars)"
              required
              pattern="[0-9a-f]{64}"
              aria-label="SHA-256 hash"
            />
            <button type="submit" className="wb-btn wb-btn-primary">
              Add File
            </button>
          </div>
        </form>
        <div className="wb-repro-actions">
          <label>
            <input
              type="radio"
              name="action"
              value="evaluate"
              checked={state.action === 'evaluate'}
              onChange={() => setState((prev) => ({ ...prev, action: 'evaluate' }))}
            />
            Evaluate
          </label>
          <label>
            <input
              type="radio"
              name="action"
              value="snapshot"
              checked={state.action === 'snapshot'}
              onChange={() => setState((prev) => ({ ...prev, action: 'snapshot' }))}
            />
            Snapshot
          </label>
        </div>
        <button
          className="wb-btn wb-btn-primary"
          onClick={run}
          disabled={Object.keys(state.files).length === 0 && state.action === 'evaluate'}
        >
          {state.action === 'evaluate' ? 'Evaluate Reproducibility' : 'Create Snapshot'}
        </button>
      </div>
    );
  }

  if (state.loading) {
    return (
      <div className="wb-reproducibility wb-repro-loading" aria-busy="true">
        <p className="wb-repro-note">Running reproducibility {state.action}…</p>
      </div>
    );
  }

  if (state.error && !state.result) {
    return (
      <div className="wb-reproducibility wb-repro-error" role="alert">
        <p className="wb-fault">{state.error}</p>
        <button className="wb-btn wb-btn-secondary" onClick={run}>
          Retry
        </button>
      </div>
    );
  }

  if (!state.result) {
    return null;
  }

  if (!state.result.ok) {
    return (
      <div className="wb-reproducibility wb-repro-error" role="alert">
        <p className="wb-fault">{state.result.reason}</p>
        <p className="wb-repro-detail">{state.result.detail}</p>
        <p className="wb-repro-remedy">{state.result.remedy}</p>
        <button className="wb-btn wb-btn-secondary" onClick={run}>
          Retry
        </button>
      </div>
    );
  }

  const { reproducible, predicates, snapshotId, timestamp } = state.result.data;

  return (
    <div className={`wb-reproducibility ${reproducible ? 'wb-repro-pass' : 'wb-repro-fail'}`} data-testid="reproducibility-panel">
      <div className="wb-repro-header">
        <h3>Reproducibility {state.action === 'evaluate' ? 'Evaluation' : 'Snapshot'}</h3>
        <span className={`wb-repro-status ${reproducible ? 'pass' : 'fail'}`}>
          {reproducible ? 'REPRODUCIBLE' : 'NOT REPRODUCIBLE'}
        </span>
      </div>

      <div className="wb-repro-meta">
        <dl>
          <dt>Timestamp</dt>
          <dd className="wb-mono">{new Date(timestamp).toLocaleString()}</dd>
          {snapshotId && (
            <>
              <dt>Snapshot ID</dt>
              <dd className="wb-mono">{snapshotId}</dd>
            </>
          )}
          <dt>Files Checked</dt>
          <dd>{Object.keys(state.files).length}</dd>
          <dt>Predicates</dt>
          <dd>{predicates.length}</dd>
        </dl>
      </div>

      <div className="wb-repro-predicates">
        <h4>Predicates</h4>
        {predicates.length === 0 ? (
          <p className="wb-repro-note">No predicates evaluated</p>
        ) : (
          <table className="wb-repro-table" role="table">
            <thead>
              <tr>
                <th scope="col">Predicate</th>
                <th scope="col">Status</th>
                <th scope="col">Detail</th>
                <th scope="col">Evidence</th>
              </tr>
            </thead>
            <tbody>
              {predicates.map((pred, i) => (
                <tr key={i} className={pred.passed ? 'wb-repro-pass-row' : 'wb-repro-fail-row'}>
                  <td className="wb-repro-name">{pred.name}</td>
                  <td>
                    <span className={`wb-repro-badge ${pred.passed ? 'pass' : 'fail'}`}>
                      {pred.passed ? 'PASS' : 'FAIL'}
                    </span>
                  </td>
                  <td className="wb-repro-detail">
                    {pred.detail}
                    {pred.expected && <div className="wb-repro-expected">Expected: <code>{pred.expected}</code></div>}
                    {pred.actual && <div className="wb-repro-actual">Actual: <code>{pred.actual}</code></div>}
                  </td>
                  <td>
                    {pred.evidenceRef ? (
                      <details className="wb-evidence-ref">
                        <summary>View Evidence</summary>
                        <dl>
                          <dt>CAS Hash</dt>
                          <dd className="wb-mono">{pred.evidenceRef.casHash}</dd>
                          <dt>Claim ID</dt>
                          <dd className="wb-mono">{pred.evidenceRef.claimId}</dd>
                          <dt>Gates</dt>
                          <dd>
                            <ul>
                              {pred.evidenceRef.gateIds.map((gate) => (
                                <li key={gate} className="wb-mono">{gate}</li>
                              ))}
                            </ul>
                          </dd>
                        </dl>
                      </details>
                    ) : (
                      <span className="wb-repro-no-evidence">No evidence ref</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="wb-repro-files">
        <h4>Tracked Files</h4>
        <form onSubmit={handleNewFile} className="wb-repro-add-file">
          <input
            type="text"
            name="path"
            placeholder="path/to/file"
            required
            aria-label="File path"
          />
          <input
            type="text"
            name="sha256"
            placeholder="sha256 hash (64 hex chars)"
            required
            pattern="[0-9a-f]{64}"
            aria-label="SHA-256 hash"
          />
          <button type="submit" className="wb-btn wb-btn-secondary">
            Add File
          </button>
        </form>
        <ul className="wb-repro-file-list">
          {Object.entries(state.files).map(([path, sha256]) => (
            <li key={path} className="wb-repro-file-item">
              <span className="wb-mono wb-file-path">{path}</span>
              <span className="wb-mono wb-file-hash">{sha256}</span>
              <button
                type="button"
                className="wb-btn wb-btn-ghost wb-btn-sm"
                onClick={() => removeFile(path)}
                aria-label={`Remove ${path}`}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      </div>

      <div className="wb-repro-actions">
        <label>
          <input
            type="radio"
            name="action"
            value="evaluate"
            checked={state.action === 'evaluate'}
            onChange={() => setState((prev) => ({ ...prev, action: 'evaluate' }))}
          />
          Evaluate
        </label>
        <label>
          <input
            type="radio"
            name="action"
            value="snapshot"
            checked={state.action === 'snapshot'}
            onChange={() => setState((prev) => ({ ...prev, action: 'snapshot' }))}
          />
          Snapshot
        </label>
        <button className="wb-btn wb-btn-primary" onClick={run} disabled={state.loading}>
          {state.loading ? 'Running…' : state.action === 'evaluate' ? 'Re-evaluate' : 'Create Snapshot'}
        </button>
      </div>
    </div>
  );
}