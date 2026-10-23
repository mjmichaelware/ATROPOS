/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, use } from 'react';
import { ProjectHeader } from '@/components/project/project-header';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { StatusBadge } from '@/components/ui/status-badge';
import { useProject, useAgents } from '@/lib/api-atropos/hooks';
import { useAppContext } from '@/lib/contexts/app-context';
import { AgentMeshVisualizer } from '@/components/agents/agent-mesh-visualizer';
import { AgentAssignmentModal, AgentAssignmentData } from '@/components/agents/agent-assignment-modal';
import { Users, Plus, Cpu, Shield, Layers, Sparkles } from 'lucide-react';

export default function AgentsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: projectId } = use(params);
  const { data: project } = useProject(projectId);
  const { data: initialAgents, loading: agentsLoading } = useAgents(projectId);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<'roster' | 'mesh'>('mesh');

  const [localAgents, setLocalAgents] = useState<Array<AgentAssignmentData & { id: string; status: 'working' | 'idle' }>>([
    {
      id: 'agent-sup-1',
      name: 'Autonomous Supervisor',
      role: 'supervisor',
      provider: 'Frontier Alpha',
      model: 'alpha-3.7-sonnet',
      primaryResponsibility: 'Orchestrating multi-tier worker lanes and Merkle verification',
      temperature: 0.2,
      maxTokens: 8192,
      mcpPermissions: ['fs_read', 'fs_write', 'terminal_exec'],
      status: 'working'
    },
    {
      id: 'agent-ast-2',
      name: 'AST Self-Healing Specialist',
      role: 'coder',
      provider: 'Groq Realtime',
      model: 'llama-3.3-70b-versatile',
      primaryResponsibility: 'Continuous Kotlin and TypeScript syntax resolution and AST repair',
      temperature: 0.1,
      maxTokens: 16384,
      mcpPermissions: ['fs_read', 'fs_write'],
      status: 'working'
    }
  ]);

  const handleAssignAgent = (newAgentData: AgentAssignmentData) => {
    const newAgent = {
      ...newAgentData,
      id: `agent-${Date.now()}`,
      status: 'working' as const
    };
    setLocalAgents((prev) => [...prev, newAgent]);
  };

  const projectAnswers: SixAnswer = project?.six_answers || {
    objective: 'Deploy specialized autonomous agents with mathematically formal role boundaries.',
    currentOperation: `${localAgents.length} specialized agents active across supervisor and worker lanes.`,
    reasoning: 'Agents represent formal responsibilities, not conversational personas. Zero hallucination tolerance.',
    progress: { percent: 100, stage: 'Swarm Active' },
    nextAction: 'Monitor execution heartbeat or assign additional worker lanes.'
  };

  return (
    <div className="space-y-6 p-4 md:p-6 text-slate-100 font-sans">
      <ProjectHeader
        projectName={project?.name || `Project ${projectId}`}
        projectId={projectId}
        status="planning"
        answers={projectAnswers}
        trustIndicators={{
          authorityVerified: true,
          evidenceVerified: true,
          verificationComplete: true,
          policyCompliant: true,
          checkpointCurrent: true,
          recoveryAvailable: true,
          noSilentFailures: true
        }}
        availableActions={['inspect']}
        compact={false}
      />

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 bg-slate-900 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setActiveTab('mesh')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
              activeTab === 'mesh' ? 'bg-cyan-600 text-white' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            Swarm Mesh & Telemetry
          </button>
          <button
            onClick={() => setActiveTab('roster')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
              activeTab === 'roster' ? 'bg-cyan-600 text-white' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Users className="w-3.5 h-3.5" />
            Agent Roster ({localAgents.length})
          </button>
        </div>

        <button
          onClick={() => setIsModalOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-cyan-600 hover:bg-cyan-500 text-white rounded-xl text-xs font-semibold transition-colors shadow-lg shadow-cyan-900/30"
        >
          <Plus className="w-4 h-4" />
          Deploy New Agent
        </button>
      </div>

      {activeTab === 'mesh' ? (
        <AgentMeshVisualizer projectId={projectId} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {localAgents.map((agent) => (
            <div
              key={agent.id}
              className="p-5 bg-slate-900/80 border border-slate-800 rounded-xl space-y-3 hover:border-slate-700 transition-colors shadow-lg"
            >
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-semibold text-slate-100 text-sm">{agent.name}</h3>
                  <span className="text-[11px] font-mono text-cyan-400 uppercase tracking-wider">
                    {agent.role}
                  </span>
                </div>
                <StatusBadge status="working" size="sm" />
              </div>

              <p className="text-xs text-slate-300 leading-relaxed">
                {agent.primaryResponsibility}
              </p>

              <div className="pt-2 border-t border-slate-800 text-[11px] font-mono text-slate-400 flex items-center justify-between">
                <span>{agent.provider}</span>
                <span className="text-slate-200 font-semibold">{agent.model}</span>
              </div>

              <div className="flex flex-wrap gap-1.5 pt-1">
                {agent.mcpPermissions.map((perm) => (
                  <span
                    key={perm}
                    className="px-2 py-0.5 bg-slate-950 border border-slate-800 rounded text-[10px] font-mono text-slate-400"
                  >
                    {perm}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <AgentAssignmentModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onAssign={handleAssignAgent}
      />
    </div>
  );
}
