/* SPDX-License-Identifier: AGPL-3.0-only */
"use client";

import React, { FC, useState } from "react";
import { Download, ShieldCheck, Check, AlertCircle } from "lucide-react";

export interface HandoffExportProps {
  projectId: string;
  onExport: (format: "zip" | "tar") => Promise<void>;
}

export const HandoffExportUI: FC<HandoffExportProps> = ({ projectId, onExport }) => {
  const [exporting, setExporting] = useState(false);
  const [exported, setExported] = useState(false);

  const handleExport = async (format: "zip" | "tar") => {
    setExporting(true);
    try {
      await onExport(format);
      setExported(true);
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="sg-handoff-export-ui bg-slate-950/70 border border-slate-800 rounded-lg p-4 flex flex-col gap-3">
      <div className="flex items-center justify-between pb-2 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <Download className="w-4 h-4 text-emerald-400" />
          <span className="font-semibold text-sm text-slate-200">Handoff Export & Redaction Package</span>
        </div>
        <span className="text-xs text-slate-400 font-mono">Project: {projectId}</span>
      </div>

      <div className="p-3 bg-slate-900/50 rounded border border-slate-800/80 text-xs text-slate-300 space-y-1">
        <div className="flex items-center gap-1.5 text-emerald-400 font-semibold">
          <ShieldCheck className="w-3.5 h-3.5" />
          <span>Automatic Secret Non-Interference Guard Active</span>
        </div>
        <p className="text-slate-400">
          All API keys, secrets, private credentials, and absolute operator paths will be automatically redacted before package packaging.
        </p>
      </div>

      <div className="flex items-center gap-3 pt-2">
        <button
          type="button"
          disabled={exporting}
          onClick={() => handleExport("zip")}
          className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white rounded text-xs font-semibold flex items-center gap-1.5"
        >
          {exported ? <Check className="w-3.5 h-3.5" /> : <Download className="w-3.5 h-3.5" />}
          <span>{exporting ? "Packaging..." : "Export Clean ZIP"}</span>
        </button>
        <button
          type="button"
          disabled={exporting}
          onClick={() => handleExport("tar")}
          className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 rounded text-xs font-medium"
        >
          Export TAR.GZ
        </button>
      </div>
    </div>
  );
};
