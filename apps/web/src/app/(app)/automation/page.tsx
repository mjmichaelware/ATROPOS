export default function AutomationPage() {
  return (
    <div className="automation-page">
      <h1>Automation & Schedules</h1>
      <p>Manage recurring tasks and workflows</p>
      <div className="empty-state">
        <p>No automations configured</p>
      </div>
      <style jsx>{`
        .automation-page h1 { margin: 0 0 var(--sg-space-2); }
        .automation-page > p { margin: 0 0 var(--sg-space-5); color: var(--sg-text-secondary); }
        .empty-state { padding: var(--sg-space-8); text-align: center; color: var(--sg-text-muted); }
      `}</style>
    </div>
  );
}
