/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC } from "react";
import { Clock, Shield, Archive } from "lucide-react";

export interface RetentionTier {
  name: "HOT" | "WARM" | "COLD";
  retentionWindow: string;
  itemCount: number;
  policyDescription: string;
}

export interface RetentionTiersViewProps {
  tiers: RetentionTier[];
}

export const RetentionTiersView: FC<RetentionTiersViewProps> = ({ tiers }) => {
  const getTierIcon = (name: string) => {
    switch (name) {
      case "HOT":
        return <Clock className="w-4 h-4 text-amber-400" />;
      case "WARM":
        return <Archive className="w-4 h-4 text-blue-400" />;
      case "COLD":
        return <Shield className="w-4 h-4 text-emerald-400" />;
    }
  };

  return (
    <div className="sg-retention-tiers-view bg-slate-950/70 border border-slate-800 rounded-lg p-4 flex flex-col gap-3">
      <div className="flex items-center gap-2 pb-2 border-b border-slate-800">
        <Shield className="w-4 h-4 text-emerald-400" />
        <span className="font-semibold text-sm text-slate-200">Lifecycle & Retention Tiers</span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        {tiers.map((tier) => (
          <div key={tier.name} className="p-3 bg-slate-900/60 border border-slate-800 rounded flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 font-semibold text-xs text-slate-200">
                  {getTierIcon(tier.name)}
                  <span>Tier: {tier.name}</span>
                </div>
                <span className="text-[10px] text-slate-400 font-mono">{tier.retentionWindow}</span>
              </div>
              <p className="text-[11px] text-slate-400 mt-2">{tier.policyDescription}</p>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-800/80 text-xs font-mono text-emerald-400">
              {tier.itemCount} active records
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
