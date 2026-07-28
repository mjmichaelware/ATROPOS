'use client';

import { ProjectHeader } from '@/components/project/project-header';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { ControlVerb } from '@/components/ui/control-verbs';
import { Folder, Plus, Upload } from 'lucide-react';

export default function FilesPage({ params }: { params: { id: string } }) {
  const projectAnswers: SixAnswer = {
    objective: 'Manage imported, generated, modified, and exported artifacts.',
    currentOperation: 'No files in this project. Upload or generate to begin.',
    reasoning: 'Files are project-scoped with cross-project search available. Every artifact is discoverable and linked to evidence.',
    progress: { percent: 0, stage: 'Planning' },
    nextAction: 'Upload your first file or generate artifacts through workflows.',
  };

  const filesAnswers: SixAnswer = {
    objective: 'Display all project files in a consistent explorer.',
    currentOperation: 'Idle - No files available.',
    reasoning: 'One file tree for all project artifacts, with syntax highlighting and diff support.',
    progress: { percent: 0, stage: 'Idle' },
    nextAction: 'Upload or create your first project file.',
    evidence: { link: '#', label: 'View file change history' },
  };

  return (
    <div className="space-y-8">
      <ProjectHeader
        projectName={`Project ${params.id}`}
        projectId={params.id}
        status="planning"
        answers={projectAnswers}
        trustIndicators={{
          authorityVerified: true,
          evidenceVerified: false,
          verificationComplete: false,
          policyCompliant: true,
          checkpointCurrent: true,
          recoveryAvailable: false,
          noSilentFailures: true,
        }}
        availableActions={['inspect']}
        compact={false}
      />

      <div className="px-8 space-y-6">
        <section className="space-y-3">
          <h2 className="text-2xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
            Files
          </h2>
          <SixAnswersPanel answers={filesAnswers} compact={false} expandable={true} />
        </section>

        <div className="flex gap-3">
          <button className="inline-flex items-center gap-2 px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold">
            <Upload className="w-5 h-5" />
            Upload File
          </button>
          <button className="inline-flex items-center gap-2 px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors">
            <Plus className="w-5 h-5" />
            Create File
          </button>
        </div>

        <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
          <Folder className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
            Empty project directory
          </h3>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400 mb-4">
            Upload, generate, or import files to populate this project.
          </p>
        </div>
      </div>
    </div>
  );
}
