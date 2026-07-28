export default function HistoryPage() {
  return (
    <div className="history-page">
      <h1>Activity Timeline</h1>
      <p>View all events and actions across projects</p>
      <div className="timeline">
        <div className="empty-state">
          <p>No events yet</p>
        </div>
      </div>
      <style jsx>{`
        .history-page h1 { margin: 0 0 var(--sg-space-2); }
        .history-page > p { margin: 0 0 var(--sg-space-5); color: var(--sg-text-secondary); }
        .timeline { border: 1px solid var(--sg-border); border-radius: var(--sg-radius-lg); background: var(--sg-surface); }
        .empty-state { padding: var(--sg-space-8); text-align: center; color: var(--sg-text-muted); }
      `}</style>
    </div>
  );
}
