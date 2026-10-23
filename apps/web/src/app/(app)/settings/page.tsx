'use client';

import { useState, useEffect } from 'react';
import {
  Wifi,
  Key,
  ShieldAlert,
  Sliders,
  ExternalLink,
  Save,
  Activity,
  CheckCircle2,
  XCircle,
  Eye,
  EyeOff,
  Radio,
  Server,
  Sparkles,
  Smartphone
} from 'lucide-react';
import { useSessionState } from '@/lib/contexts/session-state-context';

interface ProviderConfig {
  id: string;
  name: string;
  envKey: string;
  key: string;
  enabled: boolean;
  tier: 'free' | 'standard' | 'frontier';
  status: 'unknown' | 'testing' | 'healthy' | 'error';
  latencyMs?: number;
}

/**
 * Cyber-HUD Multi-Column Settings Matrix for ATROPOS Sovereign Web Workstation.
 *
 * Provides dense, multi-column and multi-row configuration across:
 * - Engine Bridge & Network Topology
 * - Neural Provider Credentials & Cascade Priorities
 * - Sovereign Autonomous Execution Policies
 * - SpecGraph Foundry Independent Vercel Production
 * - Live Preview Studio Preferences
 */
export default function SettingsMatrixPage() {
  const { session, setDeveloperTools } = useSessionState();

  // Column 1: Bridge Networking
  const [bridgeHost, setBridgeHost] = useState('127.0.0.1');
  const [bridgePort, setBridgePort] = useState('4317');
  const [bridgeProtocol, setBridgeProtocol] = useState<'http' | 'https'>('http');
  const [bridgeStatus, setBridgeStatus] = useState<'checking' | 'connected' | 'disconnected'>('checking');
  const [bridgeLatency, setBridgeLatency] = useState<number | null>(null);

  // Column 2: Provider Matrix
  const [providers, setProviders] = useState<ProviderConfig[]>([
    { id: 'anthropic', name: 'Anthropic Claude', envKey: 'ANTHROPIC_API_KEY', key: '', enabled: true, tier: 'frontier', status: 'unknown' },
    { id: 'openai', name: 'OpenAI GPT-4o', envKey: 'OPENAI_API_KEY', key: '', enabled: true, tier: 'frontier', status: 'unknown' },
    { id: 'gemini', name: 'Google Gemini Pro', envKey: 'GEMINI_API_KEY', key: '', enabled: true, tier: 'standard', status: 'unknown' },
    { id: 'groq', name: 'Groq LPU (Fast Free)', envKey: 'GROQ_API_KEY', key: '', enabled: true, tier: 'free', status: 'unknown' },
    { id: 'deepseek', name: 'DeepSeek Direct', envKey: 'DEEPSEEK_API_KEY', key: '', enabled: true, tier: 'standard', status: 'unknown' },
    { id: 'ollama', name: 'Ollama Local (Offline)', envKey: 'OLLAMA_HOST', key: 'http://127.0.0.1:11434', enabled: true, tier: 'free', status: 'unknown' }
  ]);
  const [showKeys, setShowKeys] = useState<Record<string, boolean>>({});

  // Column 3: Sovereign Policies
  const [localOnly, setLocalOnly] = useState(false);
  const [paidGateLocked, setPaidGateLocked] = useState(true);
  const [zeroRetention, setZeroRetention] = useState(true);
  const [maxFileBytes, setMaxFileBytes] = useState(524288); // 512 KB
  const [autoApproveReadOnly, setAutoApproveReadOnly] = useState(true);

  // Column 4: SpecGraph Vercel Production
  const [specgraphUrl, setSpecgraphUrl] = useState('https://specgraph-foundry.vercel.app');

  // Column 5: Live Preview Studio
  const [defaultViewport, setDefaultViewport] = useState<'mobile' | 'tablet' | 'desktop'>('desktop');
  const [autoReloadOnSave, setAutoReloadOnSave] = useState(true);
  const [studioScale, setStudioScale] = useState(100);

  // Status feedback
  const [saveBanner, setSaveBanner] = useState<string | null>(null);

  useEffect(() => {
    // Probe bridge health on load
    checkBridgeHealth();
    // Load local storage preferences
    if (typeof window !== 'undefined') {
      const savedHost = localStorage.getItem('atropos.bridge.host');
      const savedPort = localStorage.getItem('atropos.bridge.port');
      const savedSgUrl = localStorage.getItem('atropos.specgraph.vercelUrl');
      if (savedHost) setBridgeHost(savedHost);
      if (savedPort) setBridgePort(savedPort);
      if (savedSgUrl) setSpecgraphUrl(savedSgUrl);
    }
  }, []);

  const checkBridgeHealth = async () => {
    setBridgeStatus('checking');
    const start = performance.now();
    try {
      const res = await fetch(`/api/atropos/status`, { cache: 'no-store' });
      const latency = Math.round(performance.now() - start);
      if (res.ok) {
        setBridgeStatus('connected');
        setBridgeLatency(latency);
      } else {
        setBridgeStatus('disconnected');
        setBridgeLatency(null);
      }
    } catch {
      setBridgeStatus('disconnected');
      setBridgeLatency(null);
    }
  };

  const handleSaveAll = () => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('atropos.bridge.host', bridgeHost);
      localStorage.setItem('atropos.bridge.port', bridgePort);
      localStorage.setItem('atropos.specgraph.vercelUrl', specgraphUrl);
      localStorage.setItem('atropos.preview.viewport', defaultViewport);
      localStorage.setItem('atropos.policy.localOnly', String(localOnly));
      localStorage.setItem('atropos.policy.paidGate', String(paidGateLocked));
      localStorage.setItem('atropos.policy.zeroRetention', String(zeroRetention));
    }
    setSaveBanner('Configuration matrix synchronized and saved to local storage.');
    setTimeout(() => setSaveBanner(null), 4000);
  };

  const toggleKeyVisibility = (id: string) => {
    setShowKeys((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const updateProviderKey = (id: string, value: string) => {
    setProviders((prev) =>
      prev.map((p) => (p.id === id ? { ...p, key: value } : p))
    );
  };

  const toggleProviderEnabled = (id: string) => {
    setProviders((prev) =>
      prev.map((p) => (p.id === id ? { ...p, enabled: !p.enabled } : p))
    );
  };

  return (
    <div className="space-y-8 p-6 md:p-8 max-w-7xl mx-auto text-sg-text-primary">
      {/* Page Title & Save Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-sg-border-dim pb-6">
        <div>
          <div className="flex items-center gap-3">
            <Sliders className="h-7 w-7 text-cyan-400" />
            <h1 className="text-3xl font-extrabold tracking-tight text-white font-mono">
              SYSTEM SETTINGS MATRIX
            </h1>
          </div>
          <p className="text-sm text-sg-text-secondary mt-1">
            Engine Bridge Networking, Provider Keys, Sovereign Execution Policies, and Studio Preferences
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={checkBridgeHealth}
            className="flex items-center gap-2 px-3 py-2 text-xs font-mono rounded-lg border border-sg-border-dim bg-sg-card-surface hover:bg-sg-panel-surface transition-colors"
          >
            <Activity className="h-4 w-4 text-cyan-400" />
            Probe Health
          </button>
          <button
            onClick={handleSaveAll}
            className="flex items-center gap-2 px-4 py-2 text-xs font-mono font-bold rounded-lg bg-cyan-400 text-black hover:bg-cyan-300 transition-colors shadow-lg shadow-cyan-500/20"
          >
            <Save className="h-4 w-4" />
            Save Changes
          </button>
        </div>
      </div>

      {saveBanner && (
        <div className="p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/40 text-emerald-400 text-xs font-mono flex items-center gap-2">
          <CheckCircle2 className="h-4 w-4" />
          <span>{saveBanner}</span>
        </div>
      )}

      {/* 5-Column / Multi-Row Responsive Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
        
        {/* Column 1: Bridge Networking */}
        <div className="rounded-xl border border-sg-border-dim bg-sg-panel-surface/80 backdrop-blur-md p-5 space-y-4">
          <div className="flex items-center justify-between border-b border-sg-border-dim pb-3">
            <div className="flex items-center gap-2">
              <Wifi className="h-5 w-5 text-cyan-400" />
              <h2 className="text-sm font-bold font-mono text-cyan-400 tracking-wider">
                1. BRIDGE NETWORKING
              </h2>
            </div>
            <span
              className={`px-2 py-0.5 text-[10px] font-mono rounded-full border ${
                bridgeStatus === 'connected'
                  ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400'
                  : bridgeStatus === 'checking'
                  ? 'border-amber-500/40 bg-amber-500/10 text-amber-400'
                  : 'border-rose-500/40 bg-rose-500/10 text-rose-400'
              }`}
            >
              {bridgeStatus.toUpperCase()} {bridgeLatency ? `(${bridgeLatency}ms)` : ''}
            </span>
          </div>

          <div className="space-y-3 text-xs font-mono">
            <div>
              <label className="block text-sg-text-muted mb-1">Bridge Host / IP</label>
              <input
                type="text"
                value={bridgeHost}
                onChange={(e) => setBridgeHost(e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-sg-gutter-bg border border-sg-border-dim text-white focus:outline-none focus:border-cyan-400"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sg-text-muted mb-1">Port</label>
                <input
                  type="text"
                  value={bridgePort}
                  onChange={(e) => setBridgePort(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-sg-gutter-bg border border-sg-border-dim text-white focus:outline-none focus:border-cyan-400"
                />
              </div>
              <div>
                <label className="block text-sg-text-muted mb-1">Protocol</label>
                <select
                  value={bridgeProtocol}
                  onChange={(e) => setBridgeProtocol(e.target.value as 'http' | 'https')}
                  className="w-full px-3 py-2 rounded-lg bg-sg-gutter-bg border border-sg-border-dim text-white focus:outline-none focus:border-cyan-400"
                >
                  <option value="http">HTTP (Loopback)</option>
                  <option value="https">HTTPS (TLS)</option>
                </select>
              </div>
            </div>

            <p className="text-[11px] text-sg-text-secondary leading-relaxed">
              Target engine endpoint: <code className="text-cyan-400">{bridgeProtocol}://{bridgeHost}:{bridgePort}</code>. Discovered automatically when running inside Termux daemon.
            </p>
          </div>
        </div>

        {/* Column 2: Sovereign Execution Policies */}
        <div className="rounded-xl border border-sg-border-dim bg-sg-panel-surface/80 backdrop-blur-md p-5 space-y-4">
          <div className="flex items-center gap-2 border-b border-sg-border-dim pb-3">
            <ShieldAlert className="h-5 w-5 text-emerald-400" />
            <h2 className="text-sm font-bold font-mono text-emerald-400 tracking-wider">
              2. SOVEREIGN POLICIES
            </h2>
          </div>

          <div className="space-y-3 text-xs">
            <div className="flex items-center justify-between p-2 rounded-lg bg-sg-gutter-bg border border-sg-border-dim/60">
              <div>
                <p className="font-semibold text-white">Local-Only (Air-Gap)</p>
                <p className="text-[11px] text-sg-text-muted">Sever all remote research internet planes</p>
              </div>
              <input
                type="checkbox"
                checked={localOnly}
                onChange={(e) => setLocalOnly(e.target.checked)}
                className="w-4 h-4 rounded text-emerald-500 bg-sg-card-surface border-sg-border-dim"
              />
            </div>

            <div className="flex items-center justify-between p-2 rounded-lg bg-sg-gutter-bg border border-sg-border-dim/60">
              <div>
                <p className="font-semibold text-white">Paid Emergency Gate</p>
                <p className="text-[11px] text-sg-text-muted">Lock paid API calls on free exhaust</p>
              </div>
              <input
                type="checkbox"
                checked={paidGateLocked}
                onChange={(e) => setPaidGateLocked(e.target.checked)}
                className="w-4 h-4 rounded text-emerald-500 bg-sg-card-surface border-sg-border-dim"
              />
            </div>

            <div className="flex items-center justify-between p-2 rounded-lg bg-sg-gutter-bg border border-sg-border-dim/60">
              <div>
                <p className="font-semibold text-white">Zero-Retention Mode</p>
                <p className="text-[11px] text-sg-text-muted">Never cache unverified web tokens</p>
              </div>
              <input
                type="checkbox"
                checked={zeroRetention}
                onChange={(e) => setZeroRetention(e.target.checked)}
                className="w-4 h-4 rounded text-emerald-500 bg-sg-card-surface border-sg-border-dim"
              />
            </div>

            <div>
              <label className="block text-sg-text-muted mb-1 font-mono">Max File Mutation Limit</label>
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min="65536"
                  max="2097152"
                  step="65536"
                  value={maxFileBytes}
                  onChange={(e) => setMaxFileBytes(Number(e.target.value))}
                  className="w-full accent-emerald-400"
                />
                <span className="font-mono text-emerald-400 text-xs w-16 text-right">
                  {Math.round(maxFileBytes / 1024)} KB
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Column 3: SpecGraph Foundry Independent Production */}
        <div className="rounded-xl border border-sg-border-dim bg-sg-panel-surface/80 backdrop-blur-md p-5 space-y-4">
          <div className="flex items-center justify-between border-b border-sg-border-dim pb-3">
            <div className="flex items-center gap-2">
              <Server className="h-5 w-5 text-purple-400" />
              <h2 className="text-sm font-bold font-mono text-purple-400 tracking-wider">
                3. SPECGRAPH VERCEL PROD
              </h2>
            </div>
            <a
              href={specgraphUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1 text-[11px] text-purple-400 hover:underline font-mono"
            >
              Open <ExternalLink className="h-3 w-3" />
            </a>
          </div>

          <div className="space-y-3 text-xs font-mono">
            <div>
              <label className="block text-sg-text-muted mb-1">Production URL</label>
              <input
                type="text"
                value={specgraphUrl}
                onChange={(e) => setSpecgraphUrl(e.target.value)}
                placeholder="https://specgraph-foundry.vercel.app"
                className="w-full px-3 py-2 rounded-lg bg-sg-gutter-bg border border-sg-border-dim text-white focus:outline-none focus:border-purple-400"
              />
            </div>

            <p className="text-[11px] text-sg-text-secondary leading-relaxed">
              SpecGraph Foundry is an independent compiler and execution DAG platform with its own separate Vercel production deployment lifecycle.
            </p>

            <div className="p-3 rounded-lg bg-purple-500/10 border border-purple-500/30 text-purple-300 text-[11px] space-y-1">
              <p className="font-semibold">Deployment Root Directory:</p>
              <code className="text-[10px] text-purple-200">apps/specgraph-foundry/apps/web</code>
            </div>
          </div>
        </div>

        {/* Column 4: Live Preview Studio Preferences */}
        <div className="rounded-xl border border-sg-border-dim bg-sg-panel-surface/80 backdrop-blur-md p-5 space-y-4">
          <div className="flex items-center gap-2 border-b border-sg-border-dim pb-3">
            <Sparkles className="h-5 w-5 text-amber-400" />
            <h2 className="text-sm font-bold font-mono text-amber-400 tracking-wider">
              4. LIVE PREVIEW STUDIO
            </h2>
          </div>

          <div className="space-y-3 text-xs">
            <div>
              <label className="block text-sg-text-muted mb-1 font-mono">Default Viewport</label>
              <div className="grid grid-cols-3 gap-2">
                {(['mobile', 'tablet', 'desktop'] as const).map((vp) => (
                  <button
                    key={vp}
                    onClick={() => setDefaultViewport(vp)}
                    className={`py-1.5 px-2 rounded-lg border text-xs font-mono uppercase transition-colors ${
                      defaultViewport === vp
                        ? 'border-amber-400 bg-amber-400/10 text-amber-400 font-bold'
                        : 'border-sg-border-dim bg-sg-gutter-bg text-sg-text-muted hover:text-white'
                    }`}
                  >
                    {vp}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex items-center justify-between p-2 rounded-lg bg-sg-gutter-bg border border-sg-border-dim/60">
              <div>
                <p className="font-semibold text-white">Auto-Reload on Code Save</p>
                <p className="text-[11px] text-sg-text-muted">Synchronize UI frame without page refresh</p>
              </div>
              <input
                type="checkbox"
                checked={autoReloadOnSave}
                onChange={(e) => setAutoReloadOnSave(e.target.checked)}
                className="w-4 h-4 rounded text-amber-500 bg-sg-card-surface border-sg-border-dim"
              />
            </div>

            <div>
              <label className="block text-sg-text-muted mb-1 font-mono">Zoom Scale ({studioScale}%)</label>
              <input
                type="range"
                min="50"
                max="150"
                step="25"
                value={studioScale}
                onChange={(e) => setStudioScale(Number(e.target.value))}
                className="w-full accent-amber-400"
              />
            </div>
          </div>
        </div>

        {/* Column 5: Provider Credentials Matrix (spans 2 columns on wide screens) */}
        <div className="rounded-xl border border-sg-border-dim bg-sg-panel-surface/80 backdrop-blur-md p-5 space-y-4 xl:col-span-2">
          <div className="flex items-center gap-2 border-b border-sg-border-dim pb-3">
            <Key className="h-5 w-5 text-rose-400" />
            <h2 className="text-sm font-bold font-mono text-rose-400 tracking-wider">
              5. NEURAL PROVIDERS MATRIX
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs font-mono">
            {providers.map((p) => {
              const visible = showKeys[p.id] || false;
              return (
                <div
                  key={p.id}
                  className="p-3 rounded-lg border border-sg-border-dim bg-sg-gutter-bg space-y-2"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-white">{p.name}</span>
                    <span
                      className={`text-[9px] uppercase px-1.5 py-0.5 rounded border ${
                        p.tier === 'free'
                          ? 'border-emerald-500/40 text-emerald-400'
                          : p.tier === 'standard'
                          ? 'border-cyan-500/40 text-cyan-400'
                          : 'border-purple-500/40 text-purple-400'
                      }`}
                    >
                      {p.tier}
                    </span>
                  </div>

                  <div className="relative">
                    <input
                      type={visible ? 'text' : 'password'}
                      value={p.key}
                      onChange={(e) => updateProviderKey(p.id, e.target.value)}
                      placeholder={`export ${p.envKey}=...`}
                      className="w-full px-2.5 py-1.5 pr-8 rounded bg-sg-card-surface border border-sg-border-dim text-[11px] text-white focus:outline-none focus:border-rose-400"
                    />
                    <button
                      type="button"
                      onClick={() => toggleKeyVisibility(p.id)}
                      className="absolute right-2 top-2 text-sg-text-muted hover:text-white"
                    >
                      {visible ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

      </div>
    </div>
  );
}
