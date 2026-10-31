/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 16.1: Demonstration Workspace Scaffolder and Onboarding Presentation.
 *
 * Scaffolds illustrative demonstration workspace artifacts, sample directory structures,
 * and representative README guidelines for fresh installations before user projects exist.
 *
 * ## Research: Localized Spotlight Cues for First-Time Developers
 * Researches interactive onboarding walkthrough overlays with localized CSS spotlight cues
 * (`clip-path: path(...)`) and smooth focal transitions to guide operators through
 * key workspace panels (explorer, editor, terminal, AI rail) without disrupting viewport layout.
 */

import React from 'react';

export interface SampleProjectArtifact {
  path: string;
  type: 'file' | 'directory';
  description: string;
  isDemonstrationData: boolean;
}

export const DEMO_WORKSPACE_ARTIFACTS: SampleProjectArtifact[] = [
  {
    path: 'sample-project/README.md',
    type: 'file',
    description: 'Demonstration project overview and verification guidelines',
    isDemonstrationData: true,
  },
  {
    path: 'sample-project/src/index.ts',
    type: 'file',
    description: 'Sample entrypoint demonstrating verified authority graph bindings',
    isDemonstrationData: true,
  },
  {
    path: 'sample-project/specs/requirements.spec.md',
    type: 'file',
    description: 'Sample atomic requirements decomposed with cryptographic provenance',
    isDemonstrationData: true,
  },
];

export function DemonstrationWorkspaceScaffolder() {
  return (
    <div
      className="demo-scaffolder-card p-4 rounded-lg border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 bg-sg-neutral-50 dark:bg-sg-neutral-900/50"
      data-testid="demo-workspace-scaffolder"
    >
      <div className="flex items-center gap-2 mb-2">
        <span className="px-2 py-0.5 text-xs font-semibold uppercase tracking-wider rounded bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
          Sample Demonstration Workspace
        </span>
      </div>
      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 mb-3">
        Illustrative project structure generated for fresh install orientation. This sample data is isolated from production pipelines.
      </p>
      <ul className="text-xs font-mono space-y-1 text-sg-neutral-700 dark:text-sg-neutral-300">
        {DEMO_WORKSPACE_ARTIFACTS.map((artifact) => (
          <li key={artifact.path} className="flex items-center gap-2">
            <span className="text-sg-neutral-400">◈</span>
            <span className="font-semibold">{artifact.path}</span>
            <span className="text-sg-neutral-500 text-[10px]">({artifact.description})</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
