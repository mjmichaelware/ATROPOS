/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useEffect, useState, useMemo } from 'react';

/**
 * ADD-W-017: Unified Activity Monitor Stream and Pipeline State Engine
 * Section 1.6 mandate: Operators must monitor all stages of autonomous execution
 * in a single, un-fragmented event stream.
 */

export enum MonitorStage {
  PLAN = 'PLAN',
  PROVIDER = 'PROVIDER',
  TOOL = 'TOOL',
  DIFF = 'DIFF',
  TEST = 'TEST',
  VERIFIER = 'VERIFIER',
  ARTIFACT = 'ARTIFACT',
  DEPLOY = 'DEPLOY',
  QUEUE = 'QUEUE',
  APPROVAL = 'APPROVAL',
  CONVERSATION = 'CONVERSATION',
}

export interface ActivityStreamItem {
  id: string;
  timestamp: string;
  stage: MonitorStage;
  subject: string;
  outcome: string;
  detail: string;
  isBlocked: boolean;
}

export interface UnifiedActivityStreamProps {
  projectFilter?: string;
  maxEvents?: number;
  onStageClick?: (stage: MonitorStage) => void;
}

const ALL_STAGES: MonitorStage[] = [
  MonitorStage.PLAN,
  MonitorStage.PROVIDER,
  MonitorStage.TOOL,
  MonitorStage.DIFF,
  MonitorStage.TEST,
  MonitorStage.VERIFIER,
  MonitorStage.ARTIFACT,
  MonitorStage.DEPLOY,
  MonitorStage.QUEUE,
  MonitorStage.APPROVAL,
  MonitorStage.CONVERSATION,
];

export function UnifiedActivityStream({
  projectFilter,
  maxEvents = 500,
  onStageClick,
}: UnifiedActivityStreamProps) {
  const [events, setEvents] = useState<ActivityStreamItem[]>([]);
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected'>('connecting');

  useEffect(() => {
    let isCancelled = false;
    let eventSource: EventSource | null = null;

    try {
      eventSource = new EventSource('/v1/events/stream');

      eventSource.onopen = () => {
        if (!isCancelled) setConnectionStatus('connected');
      };

      eventSource.onerror = () => {
        if (!isCancelled) setConnectionStatus('disconnected');
      };

      eventSource.onmessage = (msgEvent) => {
        if (isCancelled) return;
        try {
          const raw = JSON.parse(msgEvent.data);
          const stageKey = (raw.stage || raw.type || 'CONVERSATION').toUpperCase();
          const matchedStage = ALL_STAGES.find((s) => s === stageKey) || MonitorStage.CONVERSATION;

          const outcome = (raw.outcome || raw.status || 'OK').toUpperCase();
          const isBlocked = outcome === 'BLOCKED' || outcome === 'FAILED' || Boolean(raw.isBlocked);

          const newItem: ActivityStreamItem = {
            id: raw.id || `evt-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
            timestamp: raw.timestamp || new Date().toISOString(),
            stage: matchedStage,
            subject: raw.subject || raw.title || 'Event',
            outcome,
            detail: raw.detail || raw.message || JSON.stringify(raw),
            isBlocked,
          };

          setEvents((prev) => {
            const next = [newItem, ...prev];
            return next.slice(0, maxEvents);
          });
        } catch {
          // Ignore parse errors on ping/keepalive messages
        }
      };
    } catch {
      setConnectionStatus('disconnected');
    }

    return () => {
      isCancelled = true;
      if (eventSource) {
        eventSource.close();
      }
    };
  }, [maxEvents]);

  // Filter events if projectFilter is set
  const filteredEvents = useMemo(() => {
    if (!projectFilter) return events;
    return events.filter((e) => e.detail.includes(projectFilter) || e.subject.includes(projectFilter));
  }, [events, projectFilter]);

  // Compute stages reported vs explicit gaps
  const reportedStages = useMemo(() => {
    const set = new Set<MonitorStage>();
    for (const e of filteredEvents) {
      set.add(e.stage);
    }
    return set;
  }, [filteredEvents]);

  const hasBlocked = useMemo(() => {
    return filteredEvents.some((e) => e.isBlocked);
  }, [filteredEvents]);

  const reportedCount = reportedStages.size;
  const totalStages = ALL_STAGES.length;

  return (
    <div className="flex flex-col gap-4 rounded-xl border border-white/10 bg-[#07100D] p-5 text-white font-mono shadow-2xl">
      {/* Header and Telemetry */}
      <div className="flex flex-wrap items-center justify-between border-b border-white/10 pb-4 gap-2">
        <div>
          <h2 className="text-lg font-bold tracking-tight text-emerald-400">UNIFIED ACTIVITY MONITOR</h2>
          <p className="text-xs text-slate-400">Section 1.6 Unfragmented Execution Event Stream</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="text-xs px-2.5 py-1 rounded bg-slate-900 border border-white/10">
            Stages: <span className="text-emerald-400 font-bold">{reportedCount} of {totalStages}</span>
          </div>
          {hasBlocked ? (
            <div className="text-xs px-2.5 py-1 rounded bg-red-950 border border-red-700 text-red-300 font-bold animate-pulse">
              Pipeline blocked
            </div>
          ) : (
            <div className="text-xs px-2.5 py-1 rounded bg-emerald-950 border border-emerald-700 text-emerald-300 font-bold">
              Pipeline active
            </div>
          )}
          <span className={`inline-block h-2 w-2 rounded-full ${connectionStatus === 'connected' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
        </div>
      </div>

      {/* 11-Stage Explicit Gap Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-2 pt-2">
        {ALL_STAGES.map((stage) => {
          const isReported = reportedStages.has(stage);
          const stageEvents = filteredEvents.filter((e) => e.stage === stage);
          const stageBlocked = stageEvents.some((e) => e.isBlocked);

          return (
            <button
              key={stage}
              onClick={() => onStageClick?.(stage)}
              className={`flex flex-col text-left p-2.5 rounded-lg border transition-all ${
                stageBlocked
                  ? 'border-red-600 bg-red-950/40 text-red-200'
                  : isReported
                  ? 'border-emerald-500/40 bg-emerald-950/20 text-emerald-300 hover:border-emerald-400'
                  : 'border-white/5 bg-slate-900/40 text-slate-500'
              }`}
            >
              <span className="text-[10px] font-bold tracking-wider">{stage}</span>
              <span className="text-xs mt-1 truncate">
                {isReported ? (stageBlocked ? 'Blocked' : `${stageEvents.length} events`) : 'No events reported yet'}
              </span>
            </button>
          );
        })}
      </div>

      {/* Event Stream Listing */}
      <div className="mt-4 flex flex-col gap-2 max-h-[420px] overflow-y-auto pr-1">
        {filteredEvents.length === 0 ? (
          <div className="py-8 text-center text-xs text-slate-500">
            Waiting for activity stream events from engine...
          </div>
        ) : (
          filteredEvents.map((evt) => (
            <div
              key={evt.id}
              className={`flex items-start justify-between gap-3 p-3 rounded border text-xs ${
                evt.isBlocked
                  ? 'border-red-700/60 bg-red-950/20'
                  : 'border-white/5 bg-slate-900/50 hover:bg-slate-900/80'
              }`}
            >
              <div className="flex flex-col gap-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="px-1.5 py-0.5 rounded bg-white/10 text-[10px] font-bold text-slate-300">
                    {evt.stage}
                  </span>
                  <span className="font-semibold text-white truncate">{evt.subject}</span>
                </div>
                <p className="text-slate-400 break-all">{evt.detail}</p>
              </div>
              <div className="flex flex-col items-end gap-1 shrink-0">
                <span
                  className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                    evt.isBlocked
                      ? 'bg-red-800 text-red-100'
                      : 'bg-emerald-900/80 text-emerald-300'
                  }`}
                >
                  {evt.outcome}
                </span>
                <span className="text-[10px] text-slate-500">{evt.timestamp.slice(11, 19)}</span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
