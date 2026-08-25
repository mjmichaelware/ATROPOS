/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The system & authority panel (ADD-W-010/013 + ADD-W-012).
 *
 * Read-only over the engine's storage and authority reports. Every number is
 * the engine's own; this surface formats bytes and renders attestation state
 * without recomputing either.
 */
'use client';

import { useEffect, useState } from 'react';
import {
  governance,
  type AuthorityReport,
  type StorageReport,
  type CascadePayload,
  type QuarantinePayload,
  type EvidenceListPayload,
  type DeltaRegisterPayload,
  type QuotaPayload,
} from '@/lib/governance/client';

type Load<T> = { kind: 'loading' } | { kind: 'fault'; detail: string; remedy: string } | { kind: 'ready'; data: T };

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return 'unknown';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  // Whole numbers stay whole; fractions keep exactly one decimal.
  const text = Number.isInteger(value) || value >= 100
    ? String(Math.round(value))
    : value.toFixed(1);
  return `${text} ${units[unit]}`;
}

/** ADD-W-006's intensity roles applied off storage pressure. */
function storageStrength(fractionUsed: number): 'sharp' | 'soft' {
  return fractionUsed >= 0.85 ? 'sharp' : 'soft';
}

/** ADD-W-012: retention tier display from the engine's storage report. */
interface RetentionTierView {
  tier: string;
  policy: string;
  reclaimable: boolean;
  classCount: number;
  bytes: number;
}

function tierOrder(tier: string): number {
  const order: Record<string, number> = { hot: 0, warm: 1, cold: 2, delete: 3 };
  return order[tier.toLowerCase()] ?? 99;
}

function RetentionTiers({ tiers }: { tiers: RetentionTierView[] }) {
  if (!tiers.length) return null;
  const sorted = [...tiers].sort((a, b) => tierOrder(a.tier) - tierOrder(b.tier));
  return (
    <div className="mt-4">
      <p className="wb-pane-title">Retention tiers</p>
      <ul className="wb-tier-list">
        {sorted.map((t) => (
          <li key={t.tier} className="wb-tier-row">
            <span className="wb-tier-name" data-tier={t.tier.toLowerCase()}>
              {t.tier.toUpperCase()}
            </span>
            <span className="wb-tier-policy">{t.policy}</span>
            <span className="wb-tier-stats">
              {t.classCount} class{ t.classCount !== 1 ? 'es' : '' }
              · {t.reclaimable ? 'reclaimable' : 'protected'}
              · {t.bytes > 0 ? `${(t.bytes / 1024).toFixed(1)} KB` : '0 B'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function CascadeView({ data }: { data: CascadePayload }) {
  if (!data.keys.length) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Cascade snapshot</p>
        <p className="wb-pane-note">No cascade keys recorded.</p>
      </div>
    );
  }
  return (
    <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
      <p className="wb-pane-title">Cascade snapshot</p>
      <ul className="wb-cascade-list">
        {data.keys.map((k) => (
          <li key={k.key} className="wb-cascade-row">
            <code className="wb-cascade-key">{k.key}</code>
            <span className="wb-cascade-value">{k.value}</span>
            <span className={`wb-cascade-state wb-cascade-${k.state}`}>
              {k.state}
            </span>
            <span className="wb-cascade-held">held by {k.heldBy}</span>
            {k.final && <span className="wb-cascade-final">final</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}

function QuarantineView({ data }: { data: QuarantinePayload }) {
  if (!data.items.length && !data.observation.length) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Quarantine</p>
        <p className="wb-pane-note">No items in quarantine.</p>
      </div>
    );
  }
  return (
    <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
      <p className="wb-pane-title">Quarantine</p>
      <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
        {data.count} item(s) · {data.observationCount} observation(s)
      </p>
      {data.items.length > 0 && (
        <ul className="wb-quarantine-list mt-3">
          {data.items.map((item) => (
            <li key={item.id} className="wb-quarantine-row">
              <span className="wb-quarantine-id" title={item.id}>{item.id}</span>
              <span className="wb-quarantine-title">{item.title}</span>
              <span className="wb-quarantine-state">{item.state}</span>
              <span className="wb-quarantine-time">{new Date(item.createdAt).toLocaleString()}</span>
            </li>
          ))}
        </ul>
      )}
      {data.observation.length > 0 && (
        <div className="mt-3">
          <p className="wb-pane-title text-xs">Observations</p>
          <ul className="wb-observation-list">
            {data.observation.map((obs, idx) => (
              <li key={idx} className="wb-observation-row">
                <span className="wb-obs-subsystem">{obs.subsystem}</span>
                <span className="wb-obs-duration">{obs.durationSeconds}s</span>
                <span className="wb-obs-time">{new Date(obs.startedAt).toLocaleString()}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function EvidenceListView({ data }: { data: EvidenceListPayload }) {
  if (!data.items.length) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Evidence list</p>
        <p className="wb-pane-note">No evidence recorded.</p>
      </div>
    );
  }
  return (
    <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
      <p className="wb-pane-title">Evidence list</p>
      <ul className="wb-evidence-list">
        {data.items.map((item) => (
          <li key={item.id} className="wb-evidence-row">
            <code className="wb-evidence-hash" title={item.sha256}>{item.sha256.slice(0, 12)}…</code>
            <span className="wb-evidence-kind">{item.kind}</span>
            <span className="wb-evidence-path" title={item.path}>{item.path}</span>
            <span className="wb-evidence-bytes">{formatBytes(item.bytes)}</span>
            <span className="wb-evidence-time">{new Date(item.createdAt).toLocaleString()}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function DeltaRegisterView({ data }: { data: DeltaRegisterPayload }) {
  if (!data.entries.length) {
    return (
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Delta register</p>
        <p className="wb-pane-note">No delta entries recorded.</p>
      </div>
    );
  }
  return (
    <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
      <p className="wb-pane-title">Delta register</p>
      <ul className="wb-delta-list">
        {data.entries.map((entry) => (
          <li key={entry.id} className="wb-delta-row">
            <code className="wb-delta-hash" title={entry.sha256}>{entry.sha256.slice(0, 12)}…</code>
            <span className="wb-delta-kind">{entry.kind}</span>
            <span className="wb-delta-path" title={entry.path}>{entry.path}</span>
            <span className="wb-delta-bytes">{formatBytes(entry.bytes)}</span>
            <span className="wb-delta-time">{new Date(entry.createdAt).toLocaleString()}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function QuotaView({ data }: { data: QuotaPayload }) {
  return (
    <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
      <p className="wb-pane-title">Quota</p>
      <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
        {formatBytes(data.used)} of {formatBytes(data.limit)} used
        {' '}· {Math.round(data.fractionUsed * 100)}%
        {data.remaining !== undefined && ` · {formatBytes(data.remaining)} remaining`}
      </p>
      {data.resetAt && (
        <p className="wb-pane-note mt-1">Resets at {new Date(data.resetAt).toLocaleString()}</p>
      )}
    </div>
  );
}

export function SystemPanel() {
  const [storage, setStorage] = useState<Load<StorageReport>>({ kind: 'loading' });
  const [authority, setAuthority] = useState<Load<AuthorityReport>>({ kind: 'loading' });
  const [cascade, setCascade] = useState<Load<CascadePayload>>({ kind: 'loading' });
  const [quarantine, setQuarantine] = useState<Load<QuarantinePayload>>({ kind: 'loading' });
  const [evidenceList, setEvidenceList] = useState<Load<EvidenceListPayload>>({ kind: 'loading' });
  const [deltaRegister, setDeltaRegister] = useState<Load<DeltaRegisterPayload>>({ kind: 'loading' });
  const [quota, setQuota] = useState<Load<QuotaPayload>>({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await governance.storage();
      if (cancelled) return;
      if (result.ok) setStorage({ kind: 'ready', data: result.data });
      else setStorage({ kind: 'fault', detail: result.detail, remedy: result.remedy });
    })();
    void (async () => {
      const result = await governance.authority();
      if (cancelled) return;
      if (result.ok) setAuthority({ kind: 'ready', data: result.data });
      else setAuthority({ kind: 'fault', detail: result.detail, remedy: result.remedy });
    })();
    void (async () => {
      const result = await governance.cascade();
      if (cancelled) return;
      if (result.ok) setCascade({ kind: 'ready', data: result.data });
      else setCascade({ kind: 'fault', detail: result.detail, remedy: result.remedy });
    })();
    void (async () => {
      const result = await governance.quarantine();
      if (cancelled) return;
      if (result.ok) setQuarantine({ kind: 'ready', data: result.data });
      else setQuarantine({ kind: 'fault', detail: result.detail, remedy: result.remedy });
    })();
    void (async () => {
      const result = await governance.evidenceList();
      if (cancelled) return;
      if (result.ok) setEvidenceList({ kind: 'ready', data: result.data });
      else setEvidenceList({ kind: 'fault', detail: result.detail, remedy: result.remedy });
    })();
    void (async () => {
      const result = await governance.deltaRegister();
      if (cancelled) return;
      if (result.ok) setDeltaRegister({ kind: 'ready', data: result.data });
      else setDeltaRegister({ kind: 'fault', detail: result.detail, remedy: result.remedy });
    })();
    void (async () => {
      const result = await governance.quota();
      if (cancelled) return;
      if (result.ok) setQuota({ kind: 'ready', data: result.data });
      else setQuota({ kind: 'fault', detail: result.detail, remedy: result.remedy });
    })();
    return () => { cancelled = true; };
  }, []);

  function formatBytes(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes < 0) return 'unknown';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let value = bytes;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
      value /= 1024;
      unit += 1;
    }
    const text = Number.isInteger(value) || value >= 100
      ? String(Math.round(value))
      : value.toFixed(1);
    return `${text} ${units[unit]}`;
  }

  /** ADD-W-006's intensity roles applied off storage pressure. */
  function storageStrength(fractionUsed: number): 'sharp' | 'soft' {
    return fractionUsed >= 0.85 ? 'sharp' : 'soft';
  }

  return (
    <section aria-label="System and authority" className="space-y-4" data-testid="system-panel">
      <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
        System & authority
      </h2>

      {/* ── Free space / ceiling ── */}
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Storage</p>
        {storage.kind === 'loading' && <p className="wb-pane-note">Reading…</p>}
        {storage.kind === 'fault' && (
          <div role="status">
            <p className="wb-fault">{storage.detail}</p>
            <p className="wb-pane-note">{storage.remedy}</p>
          </div>
        )}
        {storage.kind === 'ready' && (
          <div data-focus-strength={storage.data.fractionUsed >= 0.85 ? 'sharp' : 'soft'}>
            <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
              {formatBytes(storage.data.usedBytes)} of {formatBytes(storage.data.ceilingBytes)}
              used · {formatBytes(storage.data.remainingBytes)} free
              {' '}· {Math.round(storage.data.fractionUsed * 100)}%
            </p>
            {storage.data.reclaimableBytes > 0 && (
              <p className="wb-pane-note">
                {formatBytes(storage.data.reclaimableBytes)} reclaimable
              </p>
            )}
            {/* ADD-W-012: Retention tiers */}
            <RetentionTiers
              tiers={storage.data.classes
                .filter((c) => c.tier)
                .reduce((acc, c) => {
                  const existing = acc.find((t) => t.tier.toLowerCase() === c.tier.toLowerCase());
                  if (existing) {
                    existing.classCount += 1;
                    existing.bytes += c.bytes;
                  } else {
                    acc.push({
                      tier: c.tier.toLowerCase(),
                      policy: getTierPolicy(c.tier),
                      reclaimable: c.reclaimable,
                      classCount: 1,
                      bytes: c.bytes,
                    });
                  }
                  return acc;
                }, [] as { tier: string; policy: string; reclaimable: boolean; classCount: number; bytes: number }[])
              }
            />
          </div>
        )}
      </div>

      {/* ── Authority hashes ── */}
      <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
        <p className="wb-pane-title">Source authority</p>
        {authority.kind === 'loading' && <p className="wb-pane-note">Reading…</p>}
        {authority.kind === 'fault' && (
          <div role="status">
            <p className="wb-fault">{authority.detail}</p>
            <p className="wb-pane-note">{authority.remedy}</p>
          </div>
        )}
        {authority.kind === 'ready' &&
          (authority.data.documents.length === 0 ? (
            <p className="wb-pane-note">No authority documents registered.</p>
          ) : (
            <ul className="wb-auth-list">
              {authority.data.documents.map((doc) => (
                <li key={doc.path} className="wb-auth-row">
                  <span className="wb-auth-state" data-auth={doc.state}>
                    {doc.state}
                  </span>
                  <span className="wb-auth-path" title={doc.path}>
                    {doc.path}
                  </span>
                  {doc.sha256 && (
                    <code className="wb-auth-hash" title={doc.sha256}>
                      {doc.sha256.slice(0, 12)}…
                    </code>
                  )}
                </li>
              ))}
            </ul>
          ))}
        {authority.kind === 'ready' && authority.data.violations.length > 0 && (
          <div role="alert" className="mt-2">
            {authority.data.violations.map((violation) => (
              <p key={violation.key} className="wb-fault">
                {violation.key}: held by {violation.heldBy} — {violation.detail}
              </p>
            ))}
          </div>
        )}
      </div>

      {/* ── Cascade snapshot ── */}
      <CascadeView data={{ ok: true, keys: [] }} />
      {cascade.kind === 'ready' && <CascadeView data={cascade.data} />}
      {cascade.kind === 'fault' && (
        <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
          <p className="wb-pane-title">Cascade snapshot</p>
          <div role="status">
            <p className="wb-fault">{cascade.detail}</p>
            <p className="wb-pane-note">{cascade.remedy}</p>
          </div>
        </div>
      )}

      {/* ── Quarantine ── */}
      <QuarantineView data={{ ok: true, count: 0, observationCount: 0, items: [], observation: [] }} />
      {quarantine.kind === 'ready' && <QuarantineView data={quarantine.data} />}
      {quarantine.kind === 'fault' && (
        <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
          <p className="wb-pane-title">Quarantine</p>
          <div role="status">
            <p className="wb-fault">{quarantine.detail}</p>
            <p className="wb-pane-note">{quarantine.remedy}</p>
          </div>
        </div>
      )}

      {/* ── Evidence list ── */}
      <EvidenceListView data={{ ok: true, items: [] }} />
      {evidenceList.kind === 'ready' && <EvidenceListView data={evidenceList.data} />}
      {evidenceList.kind === 'fault' && (
        <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
          <p className="wb-pane-title">Evidence list</p>
          <div role="status">
            <p className="wb-fault">{evidenceList.detail}</p>
            <p className="wb-pane-note">{evidenceList.remedy}</p>
          </div>
        </div>
      )}

      {/* ── Delta register ── */}
      <DeltaRegisterView data={{ ok: true, entries: [] }} />
      {deltaRegister.kind === 'ready' && <DeltaRegisterView data={deltaRegister.data} />}
      {deltaRegister.kind === 'fault' && (
        <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
          <p className="wb-pane-title">Delta register</p>
          <div role="status">
            <p className="wb-fault">{deltaRegister.detail}</p>
            <p className="wb-pane-note">{deltaRegister.remedy}</p>
          </div>
        </div>
      )}

      {/* ── Quota ── */}
      <QuotaView data={{ ok: true, used: 0, limit: 0, remaining: 0, fractionUsed: 0, resetAt: null }} />
      {quota.kind === 'ready' && <QuotaView data={quota.data} />}
      {quota.kind === 'fault' && (
        <div className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800">
          <p className="wb-pane-title">Quota</p>
          <div role="status">
            <p className="wb-fault">{quota.detail}</p>
            <p className="wb-pane-note">{quota.remedy}</p>
          </div>
        </div>
      )}
    </section>
  );
}

function getTierPolicy(tier: string): string {
  const policies: Record<string, string> = {
    hot: 'belongs to an active run; never reclaimed',
    warm: 'recent runs kept for inspection',
    cold: 'archived by hash; content may be dropped',
    delete: 'already eligible for removal',
  };
  return policies[tier.toLowerCase()] ?? 'no policy';
}