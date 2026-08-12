/* SPDX-License-Identifier: AGPL-3.0-only */
'use client'

import React, { useEffect, useRef, useState } from 'react'

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
    containerRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  return (
    <div className="flex flex-col gap-3 w-full">
      {messages.map((msg, idx) => (
        <div key={idx} className="message-item">
          {msg.type === 'text' && (
            <div className="prose prose-sm max-w-none">
              {msg.data}
            </div>
          )}
          {msg.type === 'approval' && (
            <ApprovalCard
              id={msg.data}
              onApprove={() => onApproval?.(msg.data, true)}
              onReject={() => onApproval?.(msg.data, false)}
            />
          )}
          {msg.type === 'error' && (
            <div className="p-3 bg-red-100 border border-red-300 rounded">
              {msg.data}
            </div>
          )}
        </div>
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
