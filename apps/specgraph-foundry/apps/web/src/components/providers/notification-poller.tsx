'use client';

import { useLiveNotifications } from '@/lib/api-atropos/use-live-notifications';

/**
 * Component that enables live notification polling
 * Should be placed early in the provider chain
 */
export function NotificationPoller() {
  useLiveNotifications(true);
  return null;
}
