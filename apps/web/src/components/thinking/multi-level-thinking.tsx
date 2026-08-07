/* SPDX-License-Identifier: AGPL-3.0-only */
'use client'

import React, { useState } from 'react'

/**
 * HOE-C06: Multi-level thinking / Evidence / Engine state.
 * Expandable drawer showing reasoning at L1-L3 depth.
 * Independent verbosity from CLI channel.
 */
interface ThinkingContent {
  depth: number  // 1-3
  text: string
  timestamp: number
}

interface MultiLevelThinkingProps {
  thinking: ThinkingContent[]
  isOpen?: boolean
  onToggle?: (open: boolean) => void
}

export function MultiLevelThinking({
  thinking,
  isOpen = false,
  onToggle
}: MultiLevelThinkingProps) {
  const [expanded, setExpanded] = useState(isOpen)
  const [selectedDepth, setSelectedDepth] = useState(1)

  const handleToggle = () => {
    setExpanded(!expanded)
    onToggle?.(!expanded)
  }

  const filtered = thinking.filter((t) => t.depth <= selectedDepth)

  return (
    <div className="border-t border-gray-200 bg-gray-50">
      <button
        onClick={handleToggle}
        className="w-full px-4 py-2 text-left font-medium hover:bg-gray-100 flex items-center gap-2"
      >
        <span className={`transition-transform ${expanded ? 'rotate-90' : ''}`}>
          ▶
        </span>
        Thinking ({thinking.length} steps)
      </button>

      {expanded && (
        <div className="p-4 border-t border-gray-200 bg-white">
          {/* Depth selector */}
          <div className="flex gap-2 mb-4 text-sm">
            {[1, 2, 3].map((d) => (
              <button
                key={d}
                onClick={() => setSelectedDepth(d)}
                className={`px-3 py-1 rounded border ${
                  selectedDepth === d
                    ? 'bg-blue-500 text-white border-blue-500'
                    : 'border-gray-300 hover:border-gray-400'
                }`}
              >
                L{d}
              </button>
            ))}
          </div>

          {/* Thinking content */}
          <div className="space-y-3 max-h-96 overflow-y-auto">
            {filtered.length === 0 ? (
              <div className="text-sm text-gray-500">No thinking at this depth</div>
            ) : (
              filtered.map((item, idx) => (
                <div
                  key={idx}
                  className="p-3 bg-gray-100 rounded text-sm leading-relaxed"
                >
                  <div className="text-xs text-gray-600 mb-1">
                    L{item.depth} · {new Date(item.timestamp).toLocaleTimeString()}
                  </div>
                  <div className="text-gray-800 whitespace-pre-wrap">{item.text}</div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}
