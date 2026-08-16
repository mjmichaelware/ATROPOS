/**
 * ATROPOS API Types
 * Core entities and responses for ATROPOS operations
 */

export type CanonicalStatus =
  | 'idle'
  | 'planning'
  | 'waiting'
  | 'working'
  | 'review-required'
  | 'blocked'
  | 'completed'
  | 'failed'
  | 'cancelled';

export type EvidenceType = 'artifact' | 'verification' | 'approval' | 'execution' | 'reference';

export interface Evidence {
  id: string;
  type: EvidenceType;
  title: string;
  link?: string;
  timestamp: string;
  impact: 'high' | 'medium' | 'low';
  verified?: boolean;
}

export interface SixAnswers {
  objective?: string;
  currentOperation?: string;
  reasoning?: string;
  progress?: {
    percent: number;
    stage?: string;
  };
  nextAction?: string;
  evidence?: Evidence[];
}

export interface Project {
  id: string;
  name: string;
  description?: string;
  created_at: string;
  updated_at: string;
  status: CanonicalStatus;
  six_answers?: SixAnswers;
  evidence?: Evidence[];
  checksum?: string;
}

export interface WorkItem {
  id: string;
  project_id: string;
  title: string;
  description?: string;
  status: CanonicalStatus;
  priority: 'low' | 'medium' | 'high';
  created_at: string;
  updated_at: string;
  progress?: number;
  six_answers?: SixAnswers;
  evidence?: Evidence[];
  /** Subject-scoped pipeline, when matching engine activity exists. */
  how?: string;
}

export interface Agent {
  id: string;
  name: string;
  description?: string;
  status: CanonicalStatus;
  current_work?: string;
  assigned_work: number;
  completed_work: number;
  blocked_work: number;
  resource_usage?: {
    cpu_percent: number;
    memory_mb: number;
    tokens_used: number;
  };
  created_at: string;
}

export interface Conversation {
  id: string;
  project_id: string;
  title: string;
  created_at: string;
  updated_at: string;
  message_count: number;
}

export interface File {
  id: string;
  project_id: string;
  name: string;
  path: string;
  type: 'file' | 'directory';
  size?: number;
  mime_type?: string;
  created_at: string;
  updated_at: string;
  content?: string;
}

export interface Approval {
  id: string;
  project_id?: string;
  work_item_id?: string;
  status: 'pending' | 'approved' | 'rejected' | 'expired' | 'superseded';
  requested_at: string;
  requested_by: string;
  action_type: string;
  reason?: string;
  responded_at?: string;
  responded_by?: string;
  response_reason?: string;
  evidence?: Evidence[];
}

export interface Notification {
  id: string;
  type: 'information' | 'suggestion' | 'approval' | 'warning' | 'failure' | 'completion';
  title: string;
  message: string;
  timestamp: string;
  read: boolean;
  action_url?: string;
  action_label?: string;
  evidence?: Evidence;
}

export interface AppError {
  id: string;
  timestamp: string;
  message: string;
  context?: string;
  technical_details?: string;
  suggested_repair?: string;
  can_retry: boolean;
  evidence?: Evidence;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, any>;
}

export interface ApiResponse<T> {
  data: T;
  error?: ApiError;
}
