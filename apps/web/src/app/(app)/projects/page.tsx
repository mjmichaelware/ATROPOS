import { Metadata } from 'next';
import Link from 'next/link';

export const metadata: Metadata = {
  title: 'Projects - ATROPOS',
};

export default function ProjectsPage() {
  return (
    <div className="projects-page">
      <div className="projects-header">
        <h1>Projects</h1>
        <Link href="/projects/new" className="btn-create">
          New Project
        </Link>
      </div>

      <div className="projects-grid">
        <div className="empty-state">
          <p>No projects yet</p>
          <Link href="/projects/new" className="btn-primary">
            Create First Project
          </Link>
        </div>
      </div>

      <style jsx>{`
        .projects-page { padding: 0; }

        .projects-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: var(--sg-space-5);
        }

        .projects-header h1 {
          margin: 0;
          font-size: var(--sg-type-2xl);
        }

        .btn-create {
          padding: var(--sg-space-2) var(--sg-space-4);
          background: var(--sg-red-600);
          color: white;
          text-decoration: none;
          border-radius: var(--sg-radius-md);
          font-weight: var(--sg-weight-medium);
          transition: background-color 0.2s;

          &:hover {
            background: var(--sg-red-700);
          }
        }

        .projects-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
          gap: var(--sg-space-4);
        }

        .empty-state {
          grid-column: 1 / -1;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: var(--sg-space-4);
          padding: var(--sg-space-12);
          text-align: center;
          color: var(--sg-text-muted);
        }

        .btn-primary {
          padding: var(--sg-space-2) var(--sg-space-4);
          background: var(--sg-red-600);
          color: white;
          text-decoration: none;
          border-radius: var(--sg-radius-md);
          font-weight: var(--sg-weight-medium);
          display: inline-block;
          transition: background-color 0.2s;

          &:hover {
            background: var(--sg-red-700);
          }
        }
      `}</style>
    </div>
  );
}
