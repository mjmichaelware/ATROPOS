import { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Models - ATROPOS',
};

export default function ModelsPage() {
  return (
    <div className="models-page">
      <h1>Model Routing</h1>
      <p>Manage provider routing and model selection</p>

      <div className="models-section">
        <h2>Available Models</h2>
        <div className="empty-state">
          <p>No models configured</p>
        </div>
      </div>

      <style jsx>{`
        .models-page { padding: 0; }
        .models-page h1 { margin: 0 0 var(--sg-space-2); font-size: var(--sg-type-2xl); }
        .models-page > p { margin: 0 0 var(--sg-space-5); color: var(--sg-text-secondary); }
        .models-section { padding: var(--sg-space-4); border: 1px solid var(--sg-border); border-radius: var(--sg-radius-lg); background: var(--sg-surface); }
        .models-section h2 { margin: 0 0 var(--sg-space-3); }
        .empty-state { padding: var(--sg-space-8); text-align: center; color: var(--sg-text-muted); }
      `}</style>
    </div>
  );
}
