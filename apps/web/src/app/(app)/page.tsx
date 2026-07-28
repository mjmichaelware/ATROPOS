import { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Home - ATROPOS',
  description: 'ATROPOS operative cockpit',
};

export default function Home() {
  return (
    <div className="home-page">
      <div className="home-header">
        <h1>ATROPOS</h1>
        <p>Unified operating environment for SpecGraph</p>
      </div>

      <div className="home-content">
        <section className="home-section">
          <h2>Recent Projects</h2>
          <div className="empty-state">
            <p>No projects yet. Create one to get started.</p>
            <a href="/projects/new" className="btn-primary">
              New Project
            </a>
          </div>
        </section>

        <section className="home-section">
          <h2>Running Jobs</h2>
          <div className="empty-state">
            <p>No active jobs</p>
          </div>
        </section>

        <section className="home-section">
          <h2>Approvals & Blockers</h2>
          <div className="empty-state">
            <p>Nothing requires attention</p>
          </div>
        </section>
      </div>

      <style jsx>{`
        .home-page {
          display: grid;
          gap: var(--sg-space-6);
        }

        .home-header {
          text-align: center;
          padding: var(--sg-space-6);

          h1 {
            margin: 0 0 var(--sg-space-2);
            font-size: 3rem;
            color: var(--sg-red-600);
          }

          p {
            margin: 0;
            color: var(--sg-text-secondary);
            font-size: var(--sg-type-lg);
          }
        }

        .home-content {
          display: grid;
          gap: var(--sg-space-5);
        }

        .home-section {
          padding: var(--sg-space-4);
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-lg);
          background: var(--sg-surface);

          h2 {
            margin: 0 0 var(--sg-space-3);
            font-size: var(--sg-type-lg);
          }
        }

        .empty-state {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: var(--sg-space-3);
          min-height: 120px;
          color: var(--sg-text-muted);

          p {
            margin: 0;
          }
        }

        .btn-primary {
          padding: var(--sg-space-2) var(--sg-space-4);
          background: var(--sg-red-600);
          color: white;
          border: none;
          border-radius: var(--sg-radius-md);
          text-decoration: none;
          cursor: pointer;
          font-weight: var(--sg-weight-medium);
          transition: background-color 0.2s;

          &:hover {
            background: var(--sg-red-700);
          }
        }
      `}</style>
    </div>
  );
}
