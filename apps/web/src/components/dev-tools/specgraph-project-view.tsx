/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * SpecGraph project detail view.
 *
 * Tabs for Sources, Atoms, Research, Executions, Plan.
 * Logic modules kept; views rebuilt on ATROPOS tokens.
 */
'use client';

import { useEffect, useState } from 'react';
import { specgraph, type SpecGraphSource, type SpecGraphAtom, type SpecGraphResearch, type SpecGraphExecution, type SpecGraphPlan, type SpecGraphResult } from '@/lib/specgraph/client';

type Tab = 'sources' | 'atoms' | 'research' | 'executions' | 'plan';

interface SpecGraphProjectViewProps {
  projectId: string;
}

export function SpecGraphProjectView({ projectId }: SpecGraphProjectViewProps) {
  const [activeTab, setActiveTab] = useState<'sources' | 'atoms' | 'research' | 'executions' | 'plan'>('sources');
  const [sources, setSources] = useState<any[]>([]);
  const [atoms, setAtoms] = useState<any[]>([]);
  const [research, setResearch] = useState<any[]>([]);
  const [executions, setExecutions] = useState<any[]>([]);
  const [plan, setPlan] = useState<any>(null);
  const [loading, setLoading] = useState<Record<string, boolean>>({
    sources: true,
    atoms: true,
    research: true,
    executions: true,
    plan: true,
  });
  const [errors, setErrors] = useState<Record<string, string | null>>({
    sources: null,
    atoms: null,
    research: null,
    executions: null,
    plan: null,
  });

  const loadTab = async (tab: 'sources' | 'atoms' | 'research' | 'executions' | 'plan') => {
    setLoading((prev) => ({ ...prev, [tab]: true }));
    setErrors((prev) => ({ ...prev, [tab]: null }));

    try {
      let result: SpecGraphResult<any>;
      switch (tab) {
        case 'sources':
          result = await specgraph.sources(projectId);
          if (result.ok) setSources(result.data.sources);
          else setErrors((p) => ({ ...p, sources: `${result.detail} ${result.remedy}` }));
          break;
        case 'atoms':
          result = await specgraph.atoms(projectId);
          if (result.ok) setAtoms(result.data.atoms);
          else setErrors((p) => ({ ...p, atoms: `${result.detail} ${result.remedy}` }));
          break;
        case 'research':
          result = await specgraph.research(projectId);
          if (result.ok) setResearch(result.data.research);
          else setErrors((p) => ({ ...p, research: `${result.detail} ${result.remedy}` }));
          break;
        case 'executions':
          result = await specgraph.executions(projectId);
          if (result.ok) setExecutions(result.data.executions);
          else setErrors((p) => ({ ...p, executions: `${result.detail} ${result.remedy}` }));
          break;
        case 'plan':
          result = await specgraph.plan(projectId);
          if (result.ok) setPlan(result.data.plan);
          else setErrors((p) => ({ ...p, plan: `${result.detail} ${result.remedy}` }));
          break;
      }
    } catch (error) {
      setErrors((p) => ({ ...p, [tab]: error instanceof Error ? error.message : 'Failed to load' }));
    } finally {
      setLoading((p) => ({ ...p, [tab]: false }));
    }
  };

  useEffect(() => {
    loadTab(activeTab);
  }, [activeTab, projectId]);

  const tabs: { id: 'sources' | 'atoms' | 'research' | 'executions' | 'plan'; label: string }[] = [
    { id: 'sources', label: 'Sources' },
    { id: 'atoms', label: 'Atoms' },
    { id: 'research', label: 'Research' },
    { id: 'executions', label: 'Executions' },
    { id: 'plan', label: 'Plan' },
  ];

  return (
    <div className="sg-specgraph-project-view" data-testid="specgraph-project-view">
      <div className="sg-specgraph-tabs" role="tablist" aria-label="SpecGraph project sections">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            role="tab"
            aria-selected={activeTab === tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`sg-specgraph-tab ${activeTab === tab.id ? 'sg-specgraph-tab-active' : ''}`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="sg-specgraph-tabpanel" role="tabpanel">
        {activeTab === 'sources' && (
          <SourcesTab sources={sources} loading={loading.sources} error={errors.sources} />
        )}
        {activeTab === 'atoms' && (
          <AtomsTab atoms={atoms} loading={loading.atoms} error={errors.atoms} />
        )}
        {activeTab === 'research' && (
          <ResearchTab research={research} loading={loading.research} error={errors.research} />
        )}
        {activeTab === 'executions' && (
          <ExecutionsTab executions={executions} loading={loading.executions} error={errors.executions} />
        )}
        {activeTab === 'plan' && (
          <PlanTab plan={plan} loading={loading.plan} error={errors.plan} />
        )}
      </div>
    </div>
  );
}

function SourcesTab({ sources, loading, error }: { sources: any[]; loading: boolean; error: string | null }) {
  if (loading) return <p className="sg-devtools-loading">Loading sources…</p>;
  if (error) return <p className="sg-devtools-error">{error}</p>;
  if (!sources.length) return <p className="sg-devtools-empty">No sources yet. Add documents via the API.</p>;

  return (
    <div className="sg-specgraph-table">
      <table>
        <thead>
          <tr>
            <th>Title</th>
            <th>Type</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          {sources.map((source) => (
            <tr key={source.id}>
              <td className="font-mono text-sm">{source.title}</td>
              <td>{source.metadata?.type ?? 'unknown'}</td>
              <td className="text-xs text-sg-neutral-500">{new Date(source.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AtomsTab({ atoms, loading, error }: { atoms: any[]; loading: boolean; error: string | null }) {
  if (loading) return <p className="sg-devtools-loading">Loading atoms…</p>;
  if (error) return <p className="sg-devtools-error">{error}</p>;
  if (!atoms.length) return <p className="sg-devtools-empty">No atoms extracted yet.</p>;

  return (
    <div className="sg-specgraph-table">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Source</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          {atoms.map((atom) => (
            <tr key={atom.id}>
              <td className="font-mono text-xs">{atom.id.slice(0, 16)}…</td>
              <td>{atom.type}</td>
              <td className="font-mono text-xs">{atom.sourceId.slice(0, 12)}…</td>
              <td className="text-xs text-sg-neutral-500">{new Date(atom.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ResearchTab({ research, loading, error }: { research: any[]; loading: boolean; error: string | null }) {
  if (loading) return <p className="sg-devtools-loading">Loading research…</p>;
  if (error) return <p className="sg-devtools-error">{error}</p>;
  if (!research.length) return <p className="sg-devtools-empty">No research queries yet.</p>;

  return (
    <div className="space-y-4">
      {research.map((r) => (
        <details key={r.id} className="sg-devtools-details">
          <summary className="sg-devtools-summary">
            <span className="font-mono">{r.query}</span>
            <span className="text-xs text-sg-neutral-500">{r.results.length} results</span>
          </summary>
          <ul className="mt-2 space-y-1">
            {r.results.map((s) => (
              <li key={s.id} className="text-sm font-mono">{s.title}</li>
            ))}
          </ul>
        </details>
      ))}
    </div>
  );
}

function ExecutionsTab({ executions, loading, error }: { executions: any[]; loading: boolean; error: string | null }) {
  if (loading) return <p className="sg-devtools-loading">Loading executions…</p>;
  if (error) return <p className="sg-devtools-error">{error}</p>;
  if (!executions.length) return <p className="sg-devtools-empty">No executions yet.</p>;

  return (
    <div className="sg-specgraph-table">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Status</th>
            <th>Started</th>
            <th>Completed</th>
          </tr>
        </thead>
        <tbody>
          {executions.map((e) => (
            <tr key={e.id}>
              <td className="font-mono text-xs">{e.id.slice(0, 16)}…</td>
              <td><span className={`sg-status sg-status-${e.status}`}>{e.status}</span></td>
              <td className="text-xs text-sg-neutral-500">{new Date(e.startedAt).toLocaleString()}</td>
              <td className="text-xs text-sg-neutral-500">{e.completedAt ? new Date(e.completedAt).toLocaleString() : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PlanTab({ plan, loading, error }: { plan: any; loading: boolean; error: string | null }) {
  if (loading) return <p className="sg-devtools-loading">Loading plan…</p>;
  if (error) return <p className="sg-devtools-error">{error}</p>;
  if (!plan) return <p className="sg-devtools-empty">No plan yet.</p>;

  return (
    <div className="space-y-4">
      <div className="sg-devtools-plan-header">
        <span className="font-semibold">Plan: {plan.status}</span>
        <span className="text-xs text-sg-neutral-500">{plan.steps.length} steps</span>
      </div>
      <ol className="space-y-2">
        {plan.steps.map((step: any) => (
          <li key={step.id} className="sg-devtools-plan-step">
            <div className="flex items-center gap-2">
              <span className={`sg-status sg-status-${step.status}`}>{step.status}</span>
              <span className="font-mono text-xs">{step.type}</span>
              <span className="text-sm">{step.description}</span>
            </div>
            {step.dependsOn.length > 0 && (
              <div className="ml-6 text-xs text-sg-neutral-500">
                Depends on: {step.dependsOn.join(', ')}
              </div>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}
