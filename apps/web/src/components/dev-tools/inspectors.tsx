'use client';

import { useState } from 'react';
import { ChevronDown, Zap, Network, Shield, Eye, RotateCcw } from 'lucide-react';

interface InspectorProps {
  title: string;
  icon: any;
  children: React.ReactNode;
  defaultOpen?: boolean;
}

function Inspector({ title, icon: Icon, children, defaultOpen = false }: InspectorProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-3 p-4 hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors text-left font-semibold text-sg-neutral-900 dark:text-sg-neutral-50"
      >
        <Icon className="w-5 h-5 text-sg-red-600" />
        {title}
        <ChevronDown
          className={`w-4 h-4 ml-auto transition-transform text-sg-neutral-600 ${open ? 'rotate-180' : ''}`}
        />
      </button>
      {open && (
        <div className="px-4 py-3 bg-sg-neutral-50 dark:bg-sg-neutral-900 border-t border-sg-neutral-200 dark:border-sg-neutral-800 max-h-96 overflow-y-auto">
          {children}
        </div>
      )}
    </div>
  );
}

export function RuntimeInspector() {
  return (
    <Inspector title="Runtime Inspector" icon={Zap} defaultOpen={true}>
      <div className="space-y-2 text-sm">
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Workflows</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Active: 0 | Queued: 0 | Failed: 0</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Events</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Processed: 0 | Pending: 0</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Resources</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Memory: -- | CPU: -- | Tokens: --</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Checkpoints</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Current: -- | Last: --</p>
        </div>
      </div>
    </Inspector>
  );
}

export function AgentInspector() {
  return (
    <Inspector title="Agent Inspector" icon={Network}>
      <div className="space-y-2 text-sm">
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Active Agents</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">0 agents assigned</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Workload</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Assigned: 0 | Completed: 0 | Blocked: 0</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Communication</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Messages: 0 | Handoffs: 0</p>
        </div>
      </div>
    </Inspector>
  );
}

export function ProviderInspector() {
  return (
    <Inspector title="Provider Inspector" icon={Network}>
      <div className="space-y-2 text-sm">
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Available Providers</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">0 providers configured</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Routing</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Current: -- | Fallback chain: --</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Metrics</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Latency: -- | Quota: -- | Cost: --</p>
        </div>
      </div>
    </Inspector>
  );
}

export function PolicyInspector() {
  return (
    <Inspector title="Policy Inspector" icon={Shield}>
      <div className="space-y-2 text-sm">
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Active Policies</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">0 policies loaded</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Restrictions</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Safety rules: 0 | Approvals required: 0</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Authority</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Source authority: -- | Verified: --</p>
        </div>
      </div>
    </Inspector>
  );
}

export function SourceAuthorityInspector() {
  return (
    <Inspector title="Source Authority Inspector" icon={Eye}>
      <div className="space-y-2 text-sm">
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Loaded Documents</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">0 documents</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Verification</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Hashes verified: 0 | Traceability: 0%</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Amendments</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Superseded documents: 0 | Evidence: --</p>
        </div>
      </div>
    </Inspector>
  );
}

export function RecoveryInspector() {
  return (
    <Inspector title="Recovery Inspector" icon={RotateCcw}>
      <div className="space-y-2 text-sm">
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Last Checkpoint</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">-- | State: --</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Recoverable State</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Agents: 0 | Workflows: 0 | History: 0</p>
        </div>
        <div className="bg-white dark:bg-sg-neutral-800 rounded p-2">
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Recovery Data</p>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Size: -- | Verified: --</p>
        </div>
      </div>
    </Inspector>
  );
}

export function AllInspectors() {
  return (
    <div className="space-y-3">
      <RuntimeInspector />
      <AgentInspector />
      <ProviderInspector />
      <PolicyInspector />
      <SourceAuthorityInspector />
      <RecoveryInspector />
    </div>
  );
}
