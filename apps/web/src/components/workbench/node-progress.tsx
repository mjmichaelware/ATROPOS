/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Node Progress Events (ADD-W-005).
 *
 * Consumes the `node_progress` event type from `/v1/events/stream`
 * and displays per-node progress spinners in the workbench.
 * Uses the async generator pattern from the events client.
 */

'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { subscribeActivity } from '@/lib/events/client';
import { ActivityEvent } from '@/lib/events/client';

export interface NodeProgressEvent {
  nodeId: string;
  nodeTitle: string;
  stage: 'starting' | 'running' | 'completed' | 'failed';
  progress: number; // 0-100
  message?: string;
  timestamp: string;
}

interface NodeProgressState {
  [nodeId: string]: {
    nodeId: string;
    nodeTitle: string;
    stage: 'starting' | 'running' | 'completed' | 'failed';
    progress: number;
    message?: string;
    lastUpdate: string;
  };
}

/**
 * Parse a node progress event from an ActivityEvent.
 */
function parseNodeProgressEvent(event: ActivityEvent): NodeProgressEvent | null {
  if (event.stage !== 'node_progress') return null;
  try {
    const parsed = JSON.parse(event.detail);
    return {
      nodeId: parsed.nodeId,
      nodeTitle: parsed.nodeTitle,
      stage: parsed.stage,
      progress: parsed.progress,
      message: parsed.message,
      timestamp: event.at,
    };
  } catch {
    return null;
  }
}

/**
 * Hook that subscribes to node progress events from the activity stream.
 */
export function useNodeProgress(): { progress: NodeProgressState; latestEvent: NodeProgressEvent | null } {
  const [progress, setProgress] = useState<NodeProgressState>({});
  const [latestEvent, setLatestEvent] = useState<NodeProgressEvent | null>(null);
  const unsubscribed = useRef(false);

  useEffect(() => {
    unsubscribed.current = false;
    let mounted = true;

    void (async () => {
      try {
        for await (const events of subscribeActivity()) {
          if (!mounted || unsubscribed.current) break;
          for (const event of events) {
            const nodeProgress = parseNodeProgressEvent(event);
            if (nodeProgress) {
              setProgress((prev) => ({
                ...prev,
                [nodeProgress.nodeId]: {
                  nodeId: nodeProgress.nodeId,
                  nodeTitle: nodeProgress.nodeTitle,
                  stage: nodeProgress.stage,
                  progress: nodeProgress.progress,
                  message: nodeProgress.message,
                  lastUpdate: nodeProgress.timestamp,
                },
              }));
              setLatestEvent(nodeProgress);
            }
          }
        }
      } catch (error) {
        // Ignore abort errors
        if (error instanceof Error && error.name !== 'AbortError') {
          console.warn('Node progress stream error:', error);
        }
      }
    })();

    return () => {
      mounted = false;
      unsubscribed.current = true;
    };
  }, []);

  return useMemo(
    () => ({ progress, latestEvent }),
    [progress, latestEvent]
  );
}

/**
 * Component that renders a node progress indicator.
 */
interface NodeProgressIndicatorProps {
  nodeId: string;
  /** Show as inline spinner in a tab/tree node. */
  inline?: boolean;
  /** Custom label when nodeTitle not in progress state. */
  fallbackLabel?: string;
}

export function NodeProgressIndicator({ nodeId, inline = true, fallbackLabel }: NodeProgressIndicatorProps) {
  const { progress } = useNodeProgress();
  const nodeProgress = progress[nodeId];

  if (!nodeProgress) {
    return inline ? (
      fallbackLabel ? <span className="wb-node-progress-idle">{fallbackLabel}</span> : null
    ) : null;
  }

  const { stage, progress: pct, nodeTitle, message } = nodeProgress;

  const spinner = (
    <svg
      className="wb-node-spinner"
      viewBox="0 0 24 24"
      aria-hidden="true"
      style={{ width: inline ? '14px' : '20px', height: inline ? '14px' : '20px' }}
    >
      <circle
        className="wb-spinner-track"
        cx="12"
        cy="12"
        r="10"
        fill="none"
        stroke="currentColor"
        strokeWidth="3"
      />
      <circle
        className="wb-spinner-progress"
        cx="12"
        cy="12"
        r="10"
        fill="none"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
        strokeDasharray={`calc(2 * 3.14159 * 10 * ${pct} / 100), calc(2 * 3.14159 * 10)`}
        style={{ transform: 'rotate(-90deg)', transformOrigin: 'center' }}
      />
    </svg>
  );

  if (inline) {
    return (
      <span className={`wb-node-progress-inline ${stage}`} title={message ?? nodeTitle}>
        {spinner}
        <span className="wb-node-progress-label">{pct > 0 ? `${Math.round(pct)}%` : stage}</span>
      </span>
    );
  }

  return (
    <div className={`wb-node-progress-card ${stage}`} role="status" aria-label={`${nodeTitle}: ${stage}`}>
      <div className="wb-node-progress-header">
        <span className="wb-node-progress-title">{nodeTitle}</span>
        <span className={`wb-node-progress-stage ${stage}`}>{stage}</span>
      </div>
      <div className="wb-node-progress-bar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div
          className="wb-node-progress-fill"
          style={{ width: `${pct}%` }}
        />
      </div>
      {message && <p className="wb-node-progress-message">{message}</p>}
    </div>
  );
}

/**
 * Component that renders a list of all active node progress events.
 */
export function NodeProgressList() {
  const { progress } = useNodeProgress();
  const entries = Object.values(progress);

  if (entries.length === 0) {
    return <p className="wb-node-progress-empty">No active node progress</p>;
  }

  return (
    <div className="wb-node-progress-list" role="list" aria-label="Node progress">
      {entries.map((node) => (
        <NodeProgressIndicator key={node.nodeId} nodeId={node.nodeId} inline={false} />
      ))}
    </div>
  );
}

/**
 * Hook for getting a single node's progress by ID.
 */
export function useNodeProgressById(nodeId: string | undefined) {
  const { progress } = useNodeProgress();
  return nodeId ? progress[nodeId] : undefined;
}