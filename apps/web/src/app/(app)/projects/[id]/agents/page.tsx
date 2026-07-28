export default function AgentsPage({ params }: { params: { id: string } }) {
  return (
    <div className="agents-page">
      <h1>Project Agents</h1>
      <div className="agents-grid">
        <div className="empty-state">
          <p>No agents assigned</p>
        </div>
      </div>
      <style jsx>{`
        .agents-page h1 { margin: 0 0 var(--sg-space-4); }
        .agents-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: var(--sg-space-3); }
        .empty-state { grid-column: 1 / -1; padding: var(--sg-space-8); text-align: center; color: var(--sg-text-muted); }
      `}</style>
    </div>
  );
}
