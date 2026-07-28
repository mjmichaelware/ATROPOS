/**
 * ATROPOS API Operations
 * High-level operations for ATROPOS entities
 */

import { atroposApi } from './client';
import { Project, WorkItem, Agent, Conversation } from './types';

export const projectOperations = {
  async list(): Promise<Project[]> {
    return atroposApi.get<Project[]>('/projects');
  },

  async get(id: string): Promise<Project> {
    return atroposApi.get<Project>(`/projects/${id}`);
  },

  async create(data: Partial<Project>): Promise<Project> {
    return atroposApi.post<Project>('/projects', data);
  },

  async update(id: string, data: Partial<Project>): Promise<Project> {
    return atroposApi.put<Project>(`/projects/${id}`, data);
  },

  async delete(id: string): Promise<void> {
    await atroposApi.delete(`/projects/${id}`);
  },
};

export const workItemOperations = {
  async list(projectId: string): Promise<WorkItem[]> {
    return atroposApi.get<WorkItem[]>(`/projects/${projectId}/work-items`);
  },

  async get(projectId: string, id: string): Promise<WorkItem> {
    return atroposApi.get<WorkItem>(`/projects/${projectId}/work-items/${id}`);
  },

  async create(projectId: string, data: Partial<WorkItem>): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(`/projects/${projectId}/work-items`, data);
  },

  async update(projectId: string, id: string, data: Partial<WorkItem>): Promise<WorkItem> {
    return atroposApi.put<WorkItem>(`/projects/${projectId}/work-items/${id}`, data);
  },
};

export const agentOperations = {
  async list(projectId: string): Promise<Agent[]> {
    return atroposApi.get<Agent[]>(`/projects/${projectId}/agents`);
  },

  async get(projectId: string, id: string): Promise<Agent> {
    return atroposApi.get<Agent>(`/projects/${projectId}/agents/${id}`);
  },
};

export const conversationOperations = {
  async list(projectId: string): Promise<Conversation[]> {
    return atroposApi.get<Conversation[]>(`/projects/${projectId}/conversations`);
  },

  async get(projectId: string, id: string): Promise<Conversation> {
    return atroposApi.get<Conversation>(`/projects/${projectId}/conversations/${id}`);
  },
};
