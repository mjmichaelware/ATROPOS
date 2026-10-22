/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState } from 'react';

/**
 * ADD-W-031: Gated Computer-Use Actuation Bridge and Surface Guard
 * Section 1.9 mandate: Autonomous GUI and computer-use tools carry severe
 * execution risk and must be strictly gated by territory and human approval.
 */

export interface ComputerUseEventData {
  actionId: string;
  actionType: 'mouse_click' | 'key_combination' | 'window_focus' | 'drag_and_drop';
  targetCoordinates?: [number, number];
  targetSurfaceName: string;
  payloadSummary: string;
  isWithinDeclaredTerritory: boolean;
}

export interface ComputerUseApprovalCardProps {
  data: ComputerUseEventData;
  onApprove: (actionId: string) => Promise<void>;
  onReject: (actionId: string, reason: string) => Promise<void>;
}

export function ComputerUseApprovalCard({
  data,
  onApprove,
  onReject,
}: ComputerUseApprovalCardProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectInput, setShowRejectInput] = useState(false);

  const handleApprove = async () => {
    if (!data.isWithinDeclaredTerritory || isSubmitting) return;
    setIsSubmitting(true);
    try {
      await onApprove(data.actionId);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReject = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await onReject(data.actionId, rejectReason || 'Operator rejected actuation');
    } finally {
      setIsSubmitting(false);
    }
  };

  const coordsDisplay = data.targetCoordinates
    ? `(${data.targetCoordinates[0]}, ${data.targetCoordinates[1]})`
    : 'N/A';

  return (
    <div className="flex flex-col gap-4 rounded-xl border-2 border-amber-500/80 bg-[#07100D] p-5 text-white font-mono shadow-2xl">
      {/* High-visibility Warning Banner */}
      <div className="flex items-center justify-between border-b border-amber-500/30 pb-3">
        <div className="flex items-center gap-2">
          <span className="h-3 w-3 rounded-full bg-amber-400 animate-ping" />
          <h3 className="text-sm font-bold text-amber-300 tracking-wider">
            GATED COMPUTER-USE ACTUATION REQUIRED
          </h3>
        </div>
        <span className="text-[10px] px-2 py-0.5 rounded bg-amber-950 border border-amber-600 text-amber-200">
          DENY BY DEFAULT
        </span>
      </div>

      {/* Surface Target Telemetry Display */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
        <div className="p-3 rounded bg-slate-900/60 border border-white/5 flex flex-col gap-1">
          <span className="text-[10px] text-slate-400 font-bold">ACTION TYPE</span>
          <span className="text-emerald-400 uppercase font-semibold">{data.actionType.replace('_', ' ')}</span>
        </div>
        <div className="p-3 rounded bg-slate-900/60 border border-white/5 flex flex-col gap-1">
          <span className="text-[10px] text-slate-400 font-bold">TARGET SURFACE</span>
          <span className="text-slate-200">
            {data.targetSurfaceName} at <span className="font-mono text-emerald-300">{coordsDisplay}</span>
          </span>
        </div>
      </div>

      {/* Payload Summary */}
      <div className="p-3 rounded bg-slate-900/40 border border-white/5 flex flex-col gap-1 text-xs">
        <span className="text-[10px] text-slate-400 font-bold">INTENDED ACTUATION PAYLOAD</span>
        <p className="text-slate-300">{data.payloadSummary}</p>
      </div>

      {/* Territory Gating Status Alert */}
      {!data.isWithinDeclaredTerritory && (
        <div className="p-3 rounded bg-red-950/60 border border-red-700 text-xs text-red-200 font-semibold">
          Actuation refused: Target is outside authorized territory
        </div>
      )}

      {/* Operator Action Bar */}
      <div className="flex flex-col gap-3 pt-2">
        {showRejectInput && (
          <input
            type="text"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="Reason for refusal..."
            className="w-full rounded bg-slate-900 border border-white/10 px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
          />
        )}

        <div className="flex items-center justify-end gap-3">
          {showRejectInput ? (
            <button
              onClick={handleReject}
              disabled={isSubmitting}
              className="px-4 py-1.5 rounded bg-red-700 hover:bg-red-600 disabled:opacity-50 text-xs font-bold text-white transition-all"
            >
              Confirm Refusal
            </button>
          ) : (
            <button
              onClick={() => setShowRejectInput(true)}
              disabled={isSubmitting}
              className="px-4 py-1.5 rounded bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-xs font-bold text-slate-300 transition-all border border-white/10"
            >
              Refuse Actuation
            </button>
          )}

          <button
            onClick={handleApprove}
            disabled={!data.isWithinDeclaredTerritory || isSubmitting}
            className={`px-5 py-1.5 rounded text-xs font-bold text-black transition-all ${
              data.isWithinDeclaredTerritory
                ? 'bg-emerald-400 hover:bg-emerald-300 cursor-pointer shadow-lg shadow-emerald-500/20'
                : 'bg-slate-700 text-slate-400 cursor-not-allowed opacity-50'
            }`}
          >
            {isSubmitting ? 'Dispatching...' : 'Approve Actuation'}
          </button>
        </div>
      </div>
    </div>
  );
}
