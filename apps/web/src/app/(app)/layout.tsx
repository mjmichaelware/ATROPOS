import { ReactNode } from 'react';
import { AppShell } from '@/components/app-shell/app-shell';

interface AppLayoutProps {
  children: ReactNode;
}

export default function AppLayout({ children }: AppLayoutProps) {
  return (
    <AppShell>
      {children}
    </AppShell>
  );
}
