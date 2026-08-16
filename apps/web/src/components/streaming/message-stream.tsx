/* SPDX-License-Identifier: AGPL-3.0-only */
'use client'

import React, { useEffect, useRef, useState } from 'react'
import { ProgressiveDisclosure } from '@/components/ui/progressive-disclosure'

/**
 * HOE-C05: Streaming + approval cards + command palette.
 * SSE/event stream consumption from bridge.
 * Pure presentation layer over existing /v1/events route.
 */
interface StreamEvent {
  type: 'text' | 'approval' | 'error' | 'complete'
  data: string
  timestamp: number
}

interface MessageStreamProps {
  eventSourceUrl: string
  onApproval?: (id: string, approved: boolean) => void
}

export function StreamingApprovalCards({ eventSourceUrl, onApproval }: MessageStreamProps) {
  const [messages, setMessages] = useState<StreamEvent[]>([])
  const [isStreaming, setIsStreaming] = useState(true)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!eventSourceUrl) return
    // Feature-detected for the same reason scrollIntoView is below: not every
    // host provides EventSource, and a stream component that throws on mount
    // takes down the view it was embedded in rather than simply not streaming.
    if (typeof EventSource === 'undefined') return

    const eventSource = new EventSource(eventSourceUrl)

    eventSource.addEventListener('message', (event) => {
      try {
        const parsed: StreamEvent = JSON.parse(event.data)
        setMessages((prev) => [...prev, parsed])

        if (parsed.type === 'complete') {
          setIsStreaming(false)
        }
      } catch (e) {
        console.error('Failed to parse event:', e)
      }
    })

    eventSource.addEventListener('error', () => {
      setIsStreaming(false)
      eventSource.close()
    })

    return () => eventSource.close()
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
      {messages.map((msg, idx) => (
        <ProgressiveDisclosure
          key={idx}
          title={msg.type === 'approval' ? 'Approval required' : `Stream item ${idx + 1}`}
          defaultExpanded={false}
          className="message-item"
        >
          {msg.type === 'text' && <div className="prose prose-sm max-w-none">{msg.data}</div>}
          {msg.type === 'approval' && (
            <ApprovalCard
              id={msg.data}
              onApprove={() => onApproval?.(msg.data, true)}
              onReject={() => onApproval?.(msg.data, false)}
            />
          )}
          {msg.type === 'error' && <div className="p-3 bg-red-100 border border-red-300 rounded">{msg.data}</div>}
        </ProgressiveDisclosure>
      ))}
      {isStreaming && (
        <div className="flex items-center gap-2 text-sm text-gray-500">
          <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse" />
          Streaming...
        </div>
      )}
      <div ref={containerRef} />
    </div>
  )
}

/** Compatibility name for existing callers; the stream remains one owner. */
export function MessageStream(props: MessageStreamProps) {
  return <StreamingApprovalCards {...props} />
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
