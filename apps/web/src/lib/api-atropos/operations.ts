/**
 * ATROPOS API Operations
 * High-level operations for ATROPOS entities
 */

import { atroposApi } from './client';
import {
  Project,
  WorkItem,
  Agent,
  Conversation,
  Approval,
  Notification,
  AppError,
} from './types';

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

  async approve(
    projectId: string,
    id: string,
    comment?: string,
    evidenceId?: string
  ): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(
      `/projects/${projectId}/work-items/${id}/approve`,
      { comment, evidence_id: evidenceId }
    );
  },

  async reject(
    projectId: string,
    id: string,
    reason: string,
    evidenceId?: string
  ): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(
      `/projects/${projectId}/work-items/${id}/reject`,
      { reason, evidence_id: evidenceId }
    );
  },

  async retry(projectId: string, id: string): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(
      `/projects/${projectId}/work-items/${id}/retry`,
      {}
    );
  },

  async pause(projectId: string, id: string): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(
      `/projects/${projectId}/work-items/${id}/pause`,
      {}
    );
  },

  async resume(projectId: string, id: string): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(
      `/projects/${projectId}/work-items/${id}/resume`,
      {}
    );
  },

  async cancel(projectId: string, id: string, reason?: string): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(
      `/projects/${projectId}/work-items/${id}/cancel`,
      { reason }
    );
  },

  async redirect(
    projectId: string,
    id: string,
    newPriority: string,
    agent?: string
  ): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(
      `/projects/${projectId}/work-items/${id}/redirect`,
      { new_priority: newPriority, agent }
    );
  },

  async prioritize(
    projectId: string,
    id: string,
    priority: 'low' | 'medium' | 'high'
  ): Promise<WorkItem> {
    return atroposApi.post<WorkItem>(
      `/projects/${projectId}/work-items/${id}/prioritize`,
      { priority }
    );
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

export const approvalOperations = {
  async list(): Promise<Approval[]> {
    return atroposApi.get<Approval[]>('/approvals');
  },

  async listByProject(projectId: string): Promise<Approval[]> {
    return atroposApi.get<Approval[]>(`/projects/${projectId}/approvals`);
  },

  async get(id: string): Promise<Approval> {
    return atroposApi.get<Approval>(`/approvals/${id}`);
  },

  async approve(id: string, comment?: string, evidenceId?: string): Promise<Approval> {
    return atroposApi.post<Approval>(`/approvals/${id}/approve`, {
      comment,
      evidence_id: evidenceId,
    });
  },

  async reject(id: string, reason: string, evidenceId?: string): Promise<Approval> {
    return atroposApi.post<Approval>(`/approvals/${id}/reject`, {
      reason,
      evidence_id: evidenceId,
    });
  },
};

export const notificationOperations = {
  async list(): Promise<Notification[]> {
    return atroposApi.get<Notification[]>('/notifications');
  },

  async get(id: string): Promise<Notification> {
    return atroposApi.get<Notification>(`/notifications/${id}`);
  },

  async markAsRead(id: string): Promise<Notification> {
    return atroposApi.post<Notification>(`/notifications/${id}/read`, {});
  },

  async delete(id: string): Promise<void> {
    await atroposApi.delete(`/notifications/${id}`);
  },
};

export const errorOperations = {
  async list(projectId?: string): Promise<AppError[]> {
    const endpoint = projectId ? `/projects/${projectId}/errors` : '/errors';
    return atroposApi.get<AppError[]>(endpoint);
  },

  async get(id: string): Promise<AppError> {
    return atroposApi.get<AppError>(`/errors/${id}`);
  },

  async retry(id: string): Promise<AppError> {
    return atroposApi.post<AppError>(`/errors/${id}/retry`, {});
  },

  async dismiss(id: string): Promise<void> {
    await atroposApi.delete(`/errors/${id}`);
  },
};
