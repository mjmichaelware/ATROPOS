/* SPDX-License-Identifier: AGPL-3.0-only */
'use client'

import React, { useState } from 'react'

/**
 * HOE-C07: Evidence drawer with morph transitions.
 * View Transition API card → drawer; shows hashes, sources, attestation.
 */
interface EvidenceItem {
  id: string
  hash: string
  source: string
  type: 'output' | 'file' | 'attestation'
  timestamp: number
  verified: boolean
}

interface EvidenceDrawerProps {
  evidence: EvidenceItem[]
  isOpen?: boolean
  onToggle?: (open: boolean) => void
}

export function EvidenceDrawer({
  evidence,
  isOpen = false,
  onToggle
}: EvidenceDrawerProps) {
  const [expanded, setExpanded] = useState(isOpen)

  const handleToggle = () => {
    const newState = !expanded
    setExpanded(newState)
    onToggle?.(newState)

    // Request View Transition API for morph effect
    if ('startViewTransition' in document) {
      (document as any).startViewTransition?.(() => {
        // Transition will be handled by CSS
      })
    }
  }

  return (
    <div className="fixed bottom-0 right-0 w-96 max-h-96 border-l border-t border-gray-200 bg-white shadow-lg">
      <button
        onClick={handleToggle}
        className="w-full px-4 py-2 font-medium bg-gray-50 hover:bg-gray-100 flex items-center gap-2 border-b"
      >
        <span className={`transition-transform ${expanded ? 'rotate-90' : ''}`}>
          ▶
        </span>
        Evidence ({evidence.length})
      </button>

      {expanded && (
        <div className="overflow-y-auto p-4 space-y-3 max-h-80">
          {evidence.length === 0 ? (
            <div className="text-sm text-gray-500">No evidence collected</div>
          ) : (
            evidence.map((item) => (
              <EvidenceItem key={item.id} item={item} />
            ))
          )}
        </div>
      )}
    </div>
  )
}

function EvidenceItem({ item }: { item: EvidenceItem }) {
  const [copied, setCopied] = useState(false)

  const copyHash = () => {
    navigator.clipboard.writeText(item.hash)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const statusIcon =
    item.type === 'attestation'
      ? item.verified
        ? '✓'
        : '✗'
      : '●'

  return (
    <div className="p-3 bg-gray-50 rounded border border-gray-200 text-sm">
      <div className="flex items-center justify-between mb-1">
        <div className="font-mono text-xs text-gray-600">
          <span className="mr-2">{statusIcon}</span>
          {item.type.toUpperCase()}
        </div>
        <button
          onClick={copyHash}
          className="text-xs px-2 py-1 bg-white border border-gray-300 rounded hover:bg-gray-100"
        >
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
      <div className="font-mono text-xs text-gray-700 break-all mb-1">
        {item.hash.slice(0, 16)}...
      </div>
      <div className="text-xs text-gray-600">
        {item.source} · {new Date(item.timestamp).toLocaleTimeString()}
      </div>
    </div>
  )
}
