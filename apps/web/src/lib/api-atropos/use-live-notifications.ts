/**
 * Hook for live notification streaming
 * Polls for new notifications at regular intervals
 */

import { useEffect, useRef } from 'react';
import { useAppContext } from '@/lib/contexts/app-context';
import { notificationOperations } from './operations';

export function useLiveNotifications(enabled = true) {
  const { addNotification } = useAppContext();
  const lastNotificationIdRef = useRef<string | null>(null);

  useEffect(() => {
    if (!enabled) return;

    const pollNotifications = async () => {
      try {
        const notifications = await notificationOperations.list();

        // Filter new notifications
        const newNotifications = notifications.filter((n) => !n.read);

        newNotifications.forEach((n) => {
          if (lastNotificationIdRef.current !== n.id) {
            addNotification({
              type: n.type,
              title: n.title,
              message: n.message,
              action_url: n.action_url,
              action_label: n.action_label,
              evidence: n.evidence,
            });

            lastNotificationIdRef.current = n.id;
          }
        });
      } catch (error) {
        console.error('Failed to poll notifications:', error);
      }
    };

    // Initial poll
    pollNotifications();

    // Poll every 5 seconds
    const interval = setInterval(pollNotifications, 5000);

    return () => clearInterval(interval);
  }, [enabled, addNotification]);
}
