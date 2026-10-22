/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React from 'react';
import { useWebDisclosure, DisclosureLevel } from '../../lib/contexts/web-disclosure-context';

export function IndependentVerbosityToggle() {
  const { level, setLevel } = useWebDisclosure();

  return (
    <div className="flex items-center gap-1 rounded bg-slate-900 border border-white/10 p-1 text-xs font-mono">
      <span className="text-[10px] text-slate-400 px-1 font-semibold">WEB DEPTH:</span>
      {[
        { lvl: DisclosureLevel.SIMPLE, label: 'L1' },
        { lvl: DisclosureLevel.STANDARD, label: 'L2' },
        { lvl: DisclosureLevel.DETAILED, label: 'L3' },
        { lvl: DisclosureLevel.INTERNAL, label: 'L4' },
      ].map(({ lvl, label }) => (
        <button
          key={lvl}
          onClick={() => setLevel(lvl)}
          className={`px-2 py-0.5 rounded text-[10px] font-bold transition-all ${
            level === lvl
              ? 'bg-emerald-500 text-black shadow'
              : 'text-slate-400 hover:text-white'
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

export default IndependentVerbosityToggle;
