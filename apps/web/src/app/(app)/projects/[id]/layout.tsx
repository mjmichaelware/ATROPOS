import { ReactNode } from 'react';
import { SessionTabBar } from '@/components/layout/session-tab-bar';

interface ProjectLayoutProps {
  children: ReactNode;
  params: Promise<{
    id: string;
  }>;
}

export default async function ProjectLayout({ children, params }: ProjectLayoutProps) {
  const { id } = await params;
  return (
    <div className="project-layout">
      <SessionTabBar projectId={id} />
      <div className="project-content">
        {children}
      </div>

      <style jsx>{`
        .project-layout {
          display: flex;
          flex-direction: column;
          height: 100%;
        }

        .project-content {
          flex: 1;
          overflow: auto;
          padding: var(--sg-space-4);
        }
      `}</style>
    </div>
  );
}
