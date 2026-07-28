import { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Work - ATROPOS',
};

export default function WorkPage({ params }: { params: { id: string } }) {
  return (
    <div className="work-page">
      <div className="work-header">
        <h1>Active Work</h1>
        <p>Goals, tasks, and approvals for project {params.id}</p>
      </div>

      <section className="work-section">
        <h2>Kanban Board</h2>
        <div className="kanban">
          <div className="kanban-column">
            <h3>To Do</h3>
            <div className="empty-column">No tasks</div>
          </div>
          <div className="kanban-column">
            <h3>In Progress</h3>
            <div className="empty-column">No tasks</div>
          </div>
          <div className="kanban-column">
            <h3>Done</h3>
            <div className="empty-column">No tasks</div>
          </div>
        </div>
      </section>

      <style jsx>{`
        .work-page {
          display: grid;
          gap: var(--sg-space-5);
        }

        .work-header {
          h1 {
            margin: 0 0 var(--sg-space-2);
          }

          p {
            margin: 0;
            color: var(--sg-text-secondary);
          }
        }

        .work-section {
          h2 {
            margin: 0 0 var(--sg-space-3);
          }
        }

        .kanban {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
          gap: var(--sg-space-3);
        }

        .kanban-column {
          padding: var(--sg-space-3);
          background: var(--sg-surface);
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-lg);

          h3 {
            margin: 0 0 var(--sg-space-3);
            font-size: var(--sg-type-sm);
            text-transform: uppercase;
            letter-spacing: 0.08em;
            color: var(--sg-text-muted);
          }
        }

        .empty-column {
          min-height: 200px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--sg-text-muted);
          font-size: var(--sg-type-sm);
        }
      `}</style>
    </div>
  );
}
