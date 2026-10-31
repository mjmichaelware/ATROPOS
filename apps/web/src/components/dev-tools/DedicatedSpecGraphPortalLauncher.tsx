/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 19.1: Dedicated SpecGraph Foundry Production Portal Launcher.
 *
 * Provides dedicated link connecting local Atropos IDE operators to the live,
 * verified SpecGraph Foundry cloud deployment on Vercel.
 *
 * ## Research: Web Deep-Linking Protocols & Custom URL Schemes
 * Researches custom URI scheme protocol handlers (`specgraph://project/<id>`) and
 * W3C Web Application Manifest `protocol_handlers` to seamlessly launch specific
 * authority graphs in cloud SpecGraph Foundry directly from local IDE context.
 */

import React from 'react';

export interface SpecGraphCloudLauncherProps {
  projectId?: string;
  productionFoundryUrl?: string;
}

export function DedicatedSpecGraphPortalLauncher({
  projectId,
  productionFoundryUrl = 'https://specgraph-foundry.vercel.app',
}: SpecGraphCloudLauncherProps) {
  const targetUrl = projectId ? `${productionFoundryUrl}/projects/${projectId}` : productionFoundryUrl;

  return (
    <div className="p-4 rounded-lg border border-cyan-500/20 bg-cyan-950/10 mb-4">
      <div className="flex items-center justify-between mb-2">
        <h4 className="font-semibold text-sm text-cyan-400">SpecGraph Foundry Cloud Portal</h4>
        <span className="text-[10px] px-2 py-0.5 rounded bg-cyan-500/10 text-cyan-300 border border-cyan-500/30">
          Production Vercel
        </span>
      </div>
      <p className="text-xs text-sg-neutral-400 mb-3">
        SpecGraph Foundry is the independent cloud authority engine for Atropos. It transforms unstructured source documents
        into atomic requirements graphs and verified handoff packages before code execution begins in this local IDE.
      </p>
      <a
        href={targetUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-md bg-cyan-600 hover:bg-cyan-500 text-white transition-colors"
      >
        <span>Launch Cloud Foundry</span>
        <span>↗</span>
      </a>
    </div>
  );
}
