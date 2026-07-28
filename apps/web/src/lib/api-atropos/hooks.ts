/**
 * React Hooks for ATROPOS API data fetching and management
 */

import { useEffect, useState, useCallback } from 'react';
import {
  Project,
  WorkItem,
  Agent,
  Conversation,
  Approval,
  Notification,
  AppError,
} from './types';
import {
  projectOperations,
  workItemOperations,
  agentOperations,
  conversationOperations,
  approvalOperations,
  notificationOperations,
  errorOperations,
} from './operations';

interface UseDataState<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
}

function useData<T>(
  fetcher: () => Promise<T>,
  deps: any[] = []
): UseDataState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let isMounted = true;

    const fetch = async () => {
      try {
        setLoading(true);
        const result = await fetcher();
        if (isMounted) {
          setData(result);
          setError(null);
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err : new Error(String(err)));
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    fetch();

    return () => {
      isMounted = false;
    };
  }, deps);

  return { data, loading, error };
}

export function useProject(id: string) {
  return useData(() => projectOperations.get(id), [id]);
}

export function useProjects() {
  return useData(() => projectOperations.list(), []);
}

export function useWorkItems(projectId: string) {
  return useData(() => workItemOperations.list(projectId), [projectId]);
}

export function useWorkItem(projectId: string, id: string) {
  return useData(() => workItemOperations.get(projectId, id), [projectId, id]);
}

export function useAgents(projectId: string) {
  return useData(() => agentOperations.list(projectId), [projectId]);
}

export function useAgent(projectId: string, id: string) {
  return useData(() => agentOperations.get(projectId, id), [projectId, id]);
}

export function useConversations(projectId: string) {
  return useData(() => conversationOperations.list(projectId), [projectId]);
}

export function useConversation(projectId: string, id: string) {
  return useData(() => conversationOperations.get(projectId, id), [projectId, id]);
}

export function useApprovals() {
  return useData(() => approvalOperations.list(), []);
}

export function useProjectApprovals(projectId: string) {
  return useData(() => approvalOperations.listByProject(projectId), [projectId]);
}

export function useNotifications() {
  return useData(() => notificationOperations.list(), []);
}

export function useErrors(projectId?: string) {
  return useData(() => errorOperations.list(projectId), [projectId]);
}

// Action hooks with callbacks
export function useWorkItemActions(projectId: string, itemId: string) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const approve = useCallback(async (comment?: string, evidenceId?: string) => {
    try {
      setLoading(true);
      await workItemOperations.approve(projectId, itemId, comment, evidenceId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [projectId, itemId]);

  const reject = useCallback(async (reason: string, evidenceId?: string) => {
    try {
      setLoading(true);
      await workItemOperations.reject(projectId, itemId, reason, evidenceId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [projectId, itemId]);

  const retry = useCallback(async () => {
    try {
      setLoading(true);
      await workItemOperations.retry(projectId, itemId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [projectId, itemId]);

  const pause = useCallback(async () => {
    try {
      setLoading(true);
      await workItemOperations.pause(projectId, itemId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [projectId, itemId]);

  const resume = useCallback(async () => {
    try {
      setLoading(true);
      await workItemOperations.resume(projectId, itemId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [projectId, itemId]);

  const cancel = useCallback(async (reason?: string) => {
    try {
      setLoading(true);
      await workItemOperations.cancel(projectId, itemId, reason);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [projectId, itemId]);

  const redirect = useCallback(async (newPriority: string, agent?: string) => {
    try {
      setLoading(true);
      await workItemOperations.redirect(projectId, itemId, newPriority, agent);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [projectId, itemId]);

  const prioritize = useCallback(async (priority: 'low' | 'medium' | 'high') => {
    try {
      setLoading(true);
      await workItemOperations.prioritize(projectId, itemId, priority);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [projectId, itemId]);

  return {
    loading,
    error,
    approve,
    reject,
    retry,
    pause,
    resume,
    cancel,
    redirect,
    prioritize,
  };
}

export function useApprovalActions(approvalId: string) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const approve = useCallback(async (comment?: string, evidenceId?: string) => {
    try {
      setLoading(true);
      await approvalOperations.approve(approvalId, comment, evidenceId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [approvalId]);

  const reject = useCallback(async (reason: string, evidenceId?: string) => {
    try {
      setLoading(true);
      await approvalOperations.reject(approvalId, reason, evidenceId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [approvalId]);

  return { loading, error, approve, reject };
}

export function useNotificationActions(notificationId: string) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const markAsRead = useCallback(async () => {
    try {
      setLoading(true);
      await notificationOperations.markAsRead(notificationId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [notificationId]);

  const dismiss = useCallback(async () => {
    try {
      setLoading(true);
      await notificationOperations.delete(notificationId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [notificationId]);

  return { loading, error, markAsRead, dismiss };
}

export function useErrorActions(errorId: string) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const retry = useCallback(async () => {
    try {
      setLoading(true);
      await errorOperations.retry(errorId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [errorId]);

  const dismiss = useCallback(async () => {
    try {
      setLoading(true);
      await errorOperations.dismiss(errorId);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [errorId]);

  return { loading, error, retry, dismiss };
}
