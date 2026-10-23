'use client';

import React, { useState, useEffect } from 'react';
import {
  Activity,
  Terminal,
  Filter,
  Trash2,
  Pause,
  Play,
  Copy,
  Check,
  Search,
  Wifi,
  Clock,
  ArrowDownCircle,
  ShieldAlert,
  Cpu
} from 'lucide-react';

export type LogSeverity = 'INFO' | 'WARN' | 'ERROR' | 'DEBUG' | 'TRACE';

export interface TelemetryEvent {
  id: string;
  timestamp: string;
  severity: LogSeverity;
  subsystem: string;
  event: string;
  payload: Record<string, unknown>;
  latencyMs?: number;
}

const INITIAL_EVENTS: TelemetryEvent[] = [
  {
    id: 'evt-001',
    timestamp: '00:00:01.120',
    severity: 'INFO',
    subsystem: 'BridgeServer',
    event: 'BOUND_PORT',
    payload: { port: 4317, protocol: 'HTTP/1.1', host: '127.0.0.1' },
    latencyMs: 1.2
  },
  {
    id: 'evt-002',
    timestamp: '00:00:01.340',
    severity: 'INFO',
    subsystem: 'TelemetryEngine',
    event: 'INITIALIZE_SIX_ANSWERS',
    payload: { objective: 'SWE-Bench Pro Target', health: 'OPTIMAL' },
    latencyMs: 0.8
  },
  {
    id: 'evt-003',
    timestamp: '00:00:01.550',
    severity: 'DEBUG',
    subsystem: 'TerritoryManager',
    event: 'RESOLVE_TERRITORIES',
    payload: { writableBoundaries: ['apps/web/', 'app/src/main/'], readOnly: ['src/main/kotlin/'] },
    latencyMs: 2.4
  },
  {
    id: 'evt-004',
    timestamp: '00:00:02.100',
    severity: 'INFO',
    subsystem: 'ProviderRouter',
    event: 'CASCADE_HEALTH_CHECK',
    payload: { primary: 'Groq-LPU-Fast', fallback: 'Gemini-2.5-Pro', roundtripMs: 42 },
    latencyMs: 42.1
  },
  {
    id: 'evt-005',
    timestamp: '00:00:02.890',
    severity: 'WARN',
    subsystem: 'QuotaLedger',
    event: 'THRESHOLD_ALERT',
    payload: { currentBurnRate: '420 tok/sec', threshold: '2000 tok/sec', status: 'SAFE' },
    latencyMs: 0.5
  },
  {
    id: 'evt-006',
    timestamp: '00:00:03.200',
    severity: 'INFO',
    subsystem: 'MerkleCertifier',
    event: 'ROOT_SEAL_COMPUTED',
    payload: { rootHash: 'sha256:7f83b1657ff1fc53b92dc18148a1d65d', selectorsPassed: 486 },
    latencyMs: 8.7
  }
];

export function TelemetryInspector() {
  const [events, setEvents] = useState<TelemetryEvent[]>(INITIAL_EVENTS);
  const [filterSeverity, setFilterSeverity] = useState<string>('ALL');
  const [subsystemSearch, setSubsystemSearch] = useState<string>('');
  const [isPaused, setIsPaused] = useState<boolean>(false);
  const [selectedEvent, setSelectedEvent] = useState<TelemetryEvent | null>(INITIAL_EVENTS[0]);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Simulated live event generation when not paused
  useEffect(() => {
    if (isPaused) return;

    const interval = setInterval(() => {
      const subsystems = ['BridgeServer', 'ProviderRouter', 'ASTHealing', 'TerritoryManager', 'QuotaLedger'];
      const actions = ['PULSE_HEARTBEAT', 'SYNC_STATE', 'INSPECT_TERRITORY', 'EVAL_INVARIANTS', 'TOKEN_TICK'];
      const chosenSub = subsystems[Math.floor(Math.random() * subsystems.length)];
      const chosenAct = actions[Math.floor(Math.random() * actions.length)];

      const newEvt: TelemetryEvent = {
        id: `evt-${Date.now().toString().slice(-4)}`,
        timestamp: new Date().toTimeString().split(' ')[0] + '.' + Math.floor(Math.random() * 900 + 100),
        severity: Math.random() > 0.85 ? 'WARN' : 'INFO',
        subsystem: chosenSub,
        event: chosenAct,
        payload: { cycleId: Math.floor(Math.random() * 1000), status: 'OK' },
        latencyMs: Number((Math.random() * 15 + 0.5).toFixed(1))
      };

      setEvents((prev) => [...prev.slice(-150), newEvt]);
    }, 4000);

    return () => clearInterval(interval);
  }, [isPaused]);

  const filteredEvents = events.filter((e) => {
    const matchesSev = filterSeverity === 'ALL' || e.severity === filterSeverity;
    const matchesSub =
      subsystemSearch.trim() === '' ||
      e.subsystem.toLowerCase().includes(subsystemSearch.toLowerCase()) ||
      e.event.toLowerCase().includes(subsystemSearch.toLowerCase());
    return matchesSev && matchesSub;
  });

  const handleCopy = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  return (
    <div className="flex flex-col h-full w-full bg-slate-950 text-slate-100 divide-y divide-slate-800">
      {/* Top Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5 bg-slate-900/70 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <Activity className="h-5 w-5 text-cyan-400" />
          <div>
            <h2 className="text-sm font-mono font-bold uppercase text-white flex items-center gap-2">
              <span>Telemetry Inspector & Event Stream</span>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-bold">
                LIVE
              </span>
            </h2>
            <p className="text-[11px] text-slate-400 font-sans">
              Real-time SSE and network trace for continuous autonomous telemetry.
            </p>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2 font-mono text-xs">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-500" />
            <input
              type="text"
              placeholder="Search event / subsystem..."
              value={subsystemSearch}
              onChange={(e) => setSubsystemSearch(e.target.value)}
              className="pl-8 pr-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-400 w-48 sm:w-60"
            />
          </div>

          <select
            value={filterSeverity}
            onChange={(e) => setFilterSeverity(e.target.value)}
            className="px-2.5 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs text-cyan-400 focus:outline-none focus:border-cyan-400"
          >
            <option value="ALL">All Severities</option>
            <option value="INFO">INFO Only</option>
            <option value="WARN">WARN Only</option>
            <option value="ERROR">ERROR Only</option>
            <option value="DEBUG">DEBUG Only</option>
          </select>

          <button
            onClick={() => setIsPaused((p) => !p)}
            title={isPaused ? 'Resume Stream' : 'Pause Stream'}
            className={`p-2 rounded-lg border transition-colors ${
              isPaused
                ? 'bg-amber-500/20 border-amber-400 text-amber-300'
                : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
            }`}
          >
            {isPaused ? <Play className="h-3.5 w-3.5" /> : <Pause className="h-3.5 w-3.5" />}
          </button>

          <button
            onClick={() => {
              setEvents([]);
              setSelectedEvent(null);
            }}
            title="Clear Stream"
            className="p-2 rounded-lg border border-slate-800 bg-slate-900 text-slate-400 hover:text-rose-400 hover:border-rose-400 transition-colors"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      {/* Main Split Content Area */}
      <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
        {/* Left: Event Stream Table */}
        <div className="flex-1 overflow-y-auto divide-y divide-slate-800/60 font-mono text-xs">
          {filteredEvents.length === 0 ? (
            <div className="text-center py-16 text-slate-500">
              No telemetry events matching current filter.
            </div>
          ) : (
            filteredEvents.map((evt) => {
              const isSelected = selectedEvent?.id === evt.id;
              return (
                <div
                  key={evt.id}
                  onClick={() => setSelectedEvent(evt)}
                  className={`flex items-center justify-between px-4 py-2.5 cursor-pointer transition-colors ${
                    isSelected
                      ? 'bg-cyan-950/40 border-l-2 border-cyan-400'
                      : 'hover:bg-slate-900/60'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <span className="text-[10px] text-slate-500 w-24 shrink-0">{evt.timestamp}</span>
                    <span
                      className={`text-[9px] px-1.5 py-0.5 rounded font-bold border shrink-0 ${
                        evt.severity === 'INFO'
                          ? 'border-cyan-500/30 text-cyan-400 bg-cyan-500/10'
                          : evt.severity === 'WARN'
                          ? 'border-amber-500/30 text-amber-400 bg-amber-500/10'
                          : evt.severity === 'ERROR'
                          ? 'border-rose-500/30 text-rose-400 bg-rose-500/10'
                          : 'border-slate-700 text-slate-400 bg-slate-800'
                      }`}
                    >
                      {evt.severity}
                    </span>
                    <span className="text-white font-bold text-xs truncate">{evt.subsystem}</span>
                    <span className="text-slate-400 text-xs truncate">::{evt.event}</span>
                  </div>

                  <div className="flex items-center gap-3 shrink-0 pl-2">
                    {evt.latencyMs !== undefined && (
                      <span className="text-[10px] text-slate-500">{evt.latencyMs}ms</span>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Right: Selected Event Inspector Drawer */}
        <div className="w-full md:w-96 border-t md:border-t-0 md:border-l border-slate-800 bg-slate-900/40 p-4 font-mono text-xs overflow-y-auto space-y-4 shrink-0">
          <div className="flex items-center justify-between border-b border-slate-800 pb-2">
            <span className="font-bold text-white flex items-center gap-1.5">
              <Terminal className="h-4 w-4 text-cyan-400" />
              EVENT INSPECTOR
            </span>
            {selectedEvent && (
              <button
                onClick={() =>
                  handleCopy(selectedEvent.id, JSON.stringify(selectedEvent, null, 2))
                }
                className="flex items-center gap-1 text-[10px] text-slate-400 hover:text-cyan-400 transition-colors"
              >
                {copiedId === selectedEvent.id ? (
                  <>
                    <Check className="h-3 w-3 text-emerald-400" />
                    <span className="text-emerald-400">Copied</span>
                  </>
                ) : (
                  <>
                    <Copy className="h-3 w-3" />
                    <span>Copy JSON</span>
                  </>
                )}
              </button>
            )}
          </div>

          {selectedEvent ? (
            <div className="space-y-3">
              <div className="space-y-1">
                <span className="text-slate-500 text-[10px]">EVENT ID</span>
                <p className="text-cyan-400 font-bold">{selectedEvent.id}</p>
              </div>

              <div className="space-y-1">
                <span className="text-slate-500 text-[10px]">SUBSYSTEM ORIGIN</span>
                <p className="text-white">{selectedEvent.subsystem}</p>
              </div>

              <div className="space-y-1">
                <span className="text-slate-500 text-[10px]">ACTION / SIGNAL</span>
                <p className="text-amber-300 font-bold">{selectedEvent.event}</p>
              </div>

              <div className="space-y-1">
                <span className="text-slate-500 text-[10px]">STRUCTURED PAYLOAD</span>
                <pre className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-[11px] text-slate-300 overflow-x-auto whitespace-pre-wrap leading-relaxed">
                  {JSON.stringify(selectedEvent.payload, null, 2)}
                </pre>
              </div>
            </div>
          ) : (
            <p className="text-slate-500 text-center py-8">Select an event from the stream to inspect.</p>
          )}
        </div>
      </div>
    </div>
  );
}
