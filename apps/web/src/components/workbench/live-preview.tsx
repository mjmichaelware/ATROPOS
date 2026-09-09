/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Live Preview Component (ADD-W-018).
 *
 * Consumes `/v1/preview` to show the factory live preview state.
 * Includes diagnostics strip showing patch fingerprint, impacted symbols,
 * and verification status. Only renders when the bridge serves the preview route.
 */

'use client';

import { useCallback, useEffect, useState } from 'react';
import { readEngine } from '@/lib/engine/client';
import { useTerritoryFocus } from '@/components/workbench/territory-focus';

interface PreviewState {
  projectId: string;
  status: 'running' | 'paused' | 'completed' | 'error';
  patchFingerprint?: string;
  impactedSymbols: string[];
  verificationStatus?: 'passed' | 'failed' | 'pending';
  diagnostics: string[];
  lastUpdate: string;
}

interface PreviewPayload {
  ok: true;
  preview: PreviewState;
}

export function LivePreview({ projectId }: { projectId: string }) {
  const [preview, setPreview] = useState<PreviewState | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { inside: inTerritory } = useTerritoryFocus(projectId);

  const load = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await readEngine<PreviewPayload>(`/v1/preview?projectId=${encodeURIComponent(projectId)}`);
      if (!result.ok) {
        setError(result.detail);
        setPreview(null);
      } else {
        setPreview(result.data.preview);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load preview');
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    if (!projectId) return;
    let cancelled = false;
    load();
    // Poll for updates every 3 seconds
    const interval = setInterval(() => {
      if (!cancelled) load();
    }, 3000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [projectId, load]);

  if (!projectId) {
    return (
      <div className="wb-preview wb-preview-empty" data-testid="live-preview">
        <p className="wb-preview-note">Select a project to see live preview</p>
      </div>
    );
  }

  if (loading && !preview) {
    return (
      <div className="wb-preview wb-preview-loading" data-testid="live-preview" aria-busy="true">
        <p className="wb-preview-note">Loading live preview…</p>
      </div>
    );
  }

  if (error && !preview) {
    return (
      <div className="wb-preview wb-preview-error" data-testid="live-preview" role="alert">
        <p className="wb-fault">{error}</p>
        <p className="wb-preview-note">The live preview route may not be available in this build.</p>
        <button type="button" className="wb-btn wb-btn-secondary" onClick={load}>
          Retry
        </button>
      </div>
    );
  }

  if (!preview) {
    return (
      <div className="wb-preview wb-preview-empty" data-testid="live-preview">
        <p className="wb-preview-note">No preview available for this project</p>
      </div>
    );
  }

  const { status, patchFingerprint, impactedSymbols, verificationStatus, diagnostics, lastUpdate } = preview;

  return (
    <div
      className={`wb-preview ${status}`}
      data-testid="live-preview"
      data-territory={inTerritory ? 'inside' : 'outside'}
    >
      <div className="wb-preview-header">
        <h3 className="wb-preview-title">Live Preview</h3>
        <span className={`wb-status-badge wb-status-${status}`}>
          {status}
        </span>
      </div>

      <div className="wb-preview-diagnostics">
        <h4>Diagnostics</h4>
        <dl className="wb-diagnostics-list">
          <dt>Project</dt>
          <dd>{projectId}</dd>
          {patchFingerprint && (
            <>
              <dt>Patch Fingerprint</dt>
              <dd className="wb-mono">{patchFingerprint}</dd>
            </>
          )}
          <dt>Impacted Symbols</dt>
          <dd>
            <ul className="wb-symbol-list">
              {impactedSymbols.length > 0 ? (
                impactedSymbols.map((sym) => <li key={sym} className="wb-mono">{sym}</li>)
              ) : (
                <li className="wb-mono wb-empty">None</li>
              )}
            </ul>
          </dd>
          {verificationStatus && (
            <>
              <dt>Verification</dt>
              <dd>
                <span className={`wb-verification-badge wb-verification-${verificationStatus}`}>
                  {verificationStatus}
                </span>
              </dd>
            </>
          )}
          <dt>Last Update</dt>
          <dd className="wb-mono">{new Date(lastUpdate).toLocaleTimeString()}</dd>
        </dl>
      </div>

      {diagnostics.length > 0 && (
        <div className="wb-preview-diagnostics wb-preview-warnings">
          <h4>Warnings</h4>
          <ul className="wb-diagnostics-list">
            {diagnostics.map((diag, i) => (
              <li key={i} className="wb-diagnostic-item">{diag}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="wb-preview-territory">
        <span className={`wb-territory-indicator ${inTerritory ? 'wb-inside' : 'wb-outside'}`} />
        <span>{inTerritory ? 'Inside territory' : 'Outside territory'}</span>
      </div>
    </div>
  );
}