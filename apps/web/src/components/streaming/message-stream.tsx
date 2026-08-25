/* SPDX-License-Identifier: AGPL-3.0-only */
'use client'

import React, { useEffect, useRef, useState } from 'react'
import { ProgressiveDisclosure } from '@/components/ui/progressive-disclosure'

/**
 * F-WEB-011: copy affordance for one streamed response.
 *
 * Clipboard write is feature-detected the same way EventSource is below: a
 * host without the API renders no button rather than throwing on mount. The
 * confirmation is text ("Copied"), not color alone (§E).
 */
function CopyResponse({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  if (typeof navigator === 'undefined' || !navigator.clipboard) return null

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1200)
    } catch {
      setCopied(false)
    }
  }

  return (
    <button
      type="button"
      onClick={copy}
      aria-label="Copy response"
      className="text-xs underline underline-offset-4 text-sg-neutral-500 hover:text-sg-neutral-800 dark:text-sg-neutral-400 dark:hover:text-sg-neutral-200"
    >
      {copied ? 'Copied' : 'Copy'}
    </button>
  )
}

/**
 * HOE-C05: Streaming + approval cards + command palette.
 * SSE/event stream consumption from bridge.
 * Pure presentation layer over existing /v1/events route.
 */
interface StreamEvent {
  type: 'text' | 'approval' | 'mcp_judged' | 'computer_use' | 'error' | 'complete'
  data: string | ActionProposalData | ComputerUseData
  timestamp: number
}

interface ActionProposalData {
  id: string
  proposal: string
  judge: string
  outcome: 'approved' | 'rejected' | 'needs_review'
  reason: string
}

interface ComputerUseData {
  id: string
  action: string
  target: string
  status: 'pending' | 'running' | 'completed' | 'failed'
  result?: string
}

interface MessageStreamProps {
  eventSourceUrl: string
  onApproval?: (id: string, approved: boolean) => void
}

export function StreamingApprovalCards({ eventSourceUrl, onApproval }: MessageStreamProps) {
  const [messages, setMessages] = useState<StreamEvent[]>([])
  // ADD-W-001: the stream claim follows the connection, never the intent.
  // Starting this true is what made a host without EventSource show a live
  // pulse over an empty list — a fabricated stream, which §0 forbids. Idle
  // until an `open` arrives; unsupported and failed are distinct words.
  const [connection, setConnection] = useState<
    'idle' | 'open' | 'closed' | 'unsupported' | 'failed'
  >('idle')
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!eventSourceUrl) return
    // Feature-detected for the same reason scrollIntoView is below: not every
    // host provides EventSource, and a stream component that throws on mount
    // takes down the view it was embedded in rather than simply not streaming.
    if (typeof EventSource === 'undefined') {
      setConnection('unsupported')
      return
    }

    const eventSource = new EventSource(eventSourceUrl)

    eventSource.addEventListener('open', () => setConnection('open'))
    eventSource.addEventListener('message', (event) => {
      try {
        const parsed: StreamEvent = JSON.parse(event.data)
        setMessages((prev) => [...prev, parsed])

        if (parsed.type === 'complete') {
          setConnection('closed')
        }
      } catch (e) {
        console.error('Failed to parse event:', e)
      }
    })

    eventSource.addEventListener('error', () => {
      setConnection('failed')
      eventSource.close()
    })

    return () => {
      eventSource.close()
      setConnection('closed')
    }
  }, [eventSourceUrl])

  useEffect(() => {
    // Feature-detected, not assumed. `scrollIntoView` is a convenience the
    // host may not implement — jsdom does not — and an effect that throws
    // takes the whole component down with it. Following the tail is worth
    // having and not worth crashing over.
    const container = containerRef.current
    if (typeof container?.scrollIntoView === 'function') {
      container.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages])

  return (
    <div className="flex flex-col gap-3 w-full">
      {messages.map((msg, idx) => {
        const getDataString = (): string => {
          if (typeof msg.data === 'string') return msg.data;
          return JSON.stringify(msg.data);
        };
        
        return (
        <ProgressiveDisclosure
          key={idx}
          title={
            msg.type === 'approval' ? 'Approval required' :
            msg.type === 'mcp_judged' ? 'MCP Judgment' :
            msg.type === 'computer_use' ? 'Computer Use' :
            `Stream item ${idx + 1}`
          }
          defaultExpanded={false}
          className="message-item"
        >
          {msg.type === 'text' && (
            <div className="prose prose-sm max-w-none">
              {getDataString()}
              <div className="mt-2">
                <CopyResponse text={getDataString()} />
              </div>
            </div>
          )}
          {msg.type === 'approval' && (
            <ApprovalCard
              id={getDataString()}
              onApprove={() => onApproval?.(getDataString(), true)}
              onReject={() => onApproval?.(getDataString(), false)}
            />
          )}
          {msg.type === 'mcp_judged' && (
            <MCPJudgedCard data={msg.data as ActionProposalData} />
          )}
          {msg.type === 'computer_use' && (
            <ComputerUseCard data={msg.data as ComputerUseData} />
          )}
          {msg.type === 'error' && <div className="p-3 bg-red-100 border border-red-300 rounded">{getDataString()}</div>}
        </ProgressiveDisclosure>
        )}
      )}      {connection === 'open' && (
        <div className="flex items-center gap-2 text-sm text-gray-500">
          {/* Motion only while a real connection carries frames (ADD-W-005);
              the pulse is suppressed under reduced motion via CSS. */}
          <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse wb-stream-live" />
          Streaming…
        </div>
      )}
      {connection === 'unsupported' && (
        <p role="status" className="text-sm text-gray-500">
          This browser cannot stream — showing nothing rather than a frozen
          stream.
        </p>
      )}
      {connection === 'failed' && messages.length === 0 && (
        <p role="status" className="text-sm text-gray-500">
          Stream unavailable — the engine did not answer at this URL.
        </p>
      )}
      <div ref={containerRef} />
    </div>
  )
}

/** Compatibility name for existing callers; the stream remains one owner. */
export function MessageStream(props: MessageStreamProps) {
  return <StreamingApprovalCards {...props} />
}

interface MCPJudgedCardProps {
  data: ActionProposalData
}

function MCPJudgedCard({ data }: MCPJudgedCardProps) {
  return (
    <div className="border-l-4 border-blue-400 bg-blue-50 p-4 rounded">
      <div className="flex items-center justify-between">
        <div className="text-sm font-medium text-blue-800">
          MCP Judgment: {data.proposal}
        </div>
        <div className="flex gap-2">
          <span className="px-2 py-1 text-xs bg-gray-200 rounded">
            {data.outcome}
          </span>
        </div>
      </div>
      <p className="mt-2 text-sm text-blue-700">{data.reason}</p>
      <p className="mt-1 text-xs text-gray-500">Judged by: {data.judge}</p>
    </div>
  )
}

interface ComputerUseCardProps {
  data: ComputerUseData
}

function ComputerUseCard({ data }: ComputerUseCardProps) {
  const statusColors = {
    pending: 'border-yellow-400 bg-yellow-50 text-yellow-800',
    running: 'border-blue-400 bg-blue-50 text-blue-800',
    completed: 'border-green-400 bg-green-50 text-green-800',
    failed: 'border-red-400 bg-red-50 text-red-800',
  };
  const statusClass = statusColors[data.status] || statusColors.pending;

  return (
    <div className={`border-l-4 p-4 rounded ${statusClass}`}>
      <div className="flex items-center justify-between">
        <div className="text-sm font-medium">
          Computer Use: {data.action}
        </div>
        <div className="flex gap-2">
          <span className="px-2 py-1 text-xs bg-gray-200 rounded capitalize">
            {data.status}
          </span>
        </div>
      </div>
      <p className="mt-2 text-sm">Target: <code className="bg-gray-200 px-1 rounded">{data.target}</code></p>
      {data.result && <p className="mt-1 text-sm text-gray-600">Result: {data.result}</p>}
    </div>
  )
}

interface ApprovalCardProps {
  id: string
  onApprove: () => void
  onReject: () => void
}

function ApprovalCard({ id, onApprove, onReject }: ApprovalCardProps) {
  return (
    <div className="border-l-4 border-yellow-400 bg-yellow-50 p-4 rounded">
      <div className="flex items-center justify-between">
        <div className="text-sm font-medium text-yellow-800">
          Approval Required
        </div>
        <div className="flex gap-2">
          <button
            onClick={onReject}
            className="px-3 py-1 text-sm bg-white border border-yellow-300 rounded hover:bg-yellow-100"
          >
            Reject
          </button>
          <button
            onClick={onApprove}
            className="px-3 py-1 text-sm bg-yellow-400 text-white rounded hover:bg-yellow-500"
          >
            Approve
          </button>
        </div>
      </div>
    </div>
  )
}
