'use client';

import { useState } from 'react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { InformationLevels, InformationLevel } from '@/components/ui/information-levels';
import { ThemeCustomizer } from '@/components/settings/theme-customizer';
import { useSessionState } from '@/lib/contexts/session-state-context';

export default function SettingsPage() {
  const { session, setDeveloperTools, setInformationLevel } = useSessionState();
  // The picker drives the real, persisted level rather than local state that
  // nothing consumed.
  const infoLevel = session.informationLevel as InformationLevel;

  const settingsAnswers: SixAnswer = {
    objective: 'Configure ATROPOS behavior, appearance, privacy, and advanced preferences.',
    currentOperation: 'Ready to customize your ATROPOS experience.',
    reasoning: 'Settings control how you interact with ATROPOS and what information is displayed.',
    progress: { percent: 100, stage: 'Configured' },
    nextAction: 'Adjust theme, information depth, or export your data.',
  };

  return (
    <div className="space-y-8 p-8 max-w-4xl mx-auto">
      {/* Page Context */}
      <section className="space-y-3">
        <h1 className="text-3xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
          Settings
        </h1>
        <SixAnswersPanel answers={settingsAnswers} compact={false} />
      </section>

      {/* Theme Settings */}
      <section className="space-y-4">
        <div className="border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-3">
          <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Theme & Appearance
          </h2>
        </div>
        <ThemeCustomizer />
      </section>

      {/* Information Depth */}
      <section className="space-y-4">
        <div className="border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-3">
          <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Information Depth
          </h2>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mt-1">
            Control how much technical detail is shown on each page
          </p>
        </div>
        <InformationLevels
          currentLevel={infoLevel}
          onLevelChange={setInformationLevel}
          compact={false}
        />
      </section>

      {/* User Preferences */}
      <section className="space-y-4">
        <div className="border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-3">
          <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            User Preferences
          </h2>
        </div>
        <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-6 space-y-4">
          <div className="flex items-center justify-between">
            <label className="text-sg-neutral-900 dark:text-sg-neutral-50 font-semibold">
              Keyboard shortcuts enabled
            </label>
            <input type="checkbox" defaultChecked className="w-5 h-5" />
          </div>
          <div className="flex items-center justify-between">
            <label className="text-sg-neutral-900 dark:text-sg-neutral-50 font-semibold">
              Auto-save projects
            </label>
            <input type="checkbox" defaultChecked className="w-5 h-5" />
          </div>
          <div className="flex items-center justify-between">
            <label className="text-sg-neutral-900 dark:text-sg-neutral-50 font-semibold">
              Show confirmation for irreversible actions
            </label>
            <input type="checkbox" defaultChecked className="w-5 h-5" />
          </div>
          <div className="flex items-center justify-between">
            <label className="text-sg-neutral-900 dark:text-sg-neutral-50 font-semibold">
              Enable notifications
            </label>
            <input type="checkbox" defaultChecked className="w-5 h-5" />
          </div>
        </div>
      </section>

      {/* Privacy & Data */}
      <section className="space-y-4">
        <div className="border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-3">
          <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Privacy & Data
          </h2>
        </div>
        <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-6 space-y-3">
          <button className="px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-100 dark:hover:bg-sg-neutral-800 transition-colors text-sm font-semibold">
            Export All Data
          </button>
          <button className="px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-100 dark:hover:bg-sg-neutral-800 transition-colors text-sm font-semibold">
            Clear Local Cache
          </button>
          <button className="px-4 py-2 border border-red-300 dark:border-red-700 text-red-600 dark:text-red-400 rounded-lg hover:bg-red-50 dark:hover:bg-red-900 transition-colors text-sm font-semibold">
            Delete All Projects
          </button>
        </div>
      </section>

      {/* Developer Options */}
      <section className="space-y-4">
        <div className="border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-3">
          <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Developer Options
          </h2>
        </div>
        <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-6 space-y-3">
          <div className="flex items-center justify-between">
            <label
              htmlFor="developer-tools-toggle"
              className="text-sg-neutral-900 dark:text-sg-neutral-50 font-semibold"
            >
              Show Developer Tools in Navigation
            </label>
            <input
              id="developer-tools-toggle"
              type="checkbox"
              className="w-5 h-5"
              checked={session.developerToolsEnabled}
              onChange={(event) => setDeveloperTools(event.target.checked)}
            />
          </div>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
            Reveals inspectors and the SpecGraph subsystem. Hidden by default so
            everyday work is not competing with runtime internals.
          </p>
        </div>
      </section>
    </div>
  );
}
