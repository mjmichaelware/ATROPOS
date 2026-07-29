import { ReactNode } from 'react';
import { SessionTabBar } from '@/components/layout/session-tab-bar';

interface ProjectLayoutProps {
  children: ReactNode;
  params: Promise<{
    id: string;
  }>;
}

/**
 * Layout classes are utilities rather than styled-jsx: this is a Server
 * Component (it awaits `params`), and styled-jsx pulls in `client-only`,
 * which fails the build from a server module.
 */
export default async function ProjectLayout({ children, params }: ProjectLayoutProps) {
  const { id } = await params;
  return (
    <div className="flex h-full flex-col">
      <SessionTabBar projectId={id} />
      <div className="flex-1 overflow-auto p-4">{children}</div>
    </div>
  );
}
