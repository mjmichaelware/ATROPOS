export default function SpecGraphPage({ params }: { params: { id: string } }) {
  return (
    <div className="specgraph-page">
      <h1>SpecGraph Integration</h1>
      <div className="specgraph-workflow">
        <div className="workflow-step">
          <div className="step-number">1</div>
          <h3>Ingest Sources</h3>
          <p>Upload and manage source documents</p>
          <a href={`/projects/${params.id}/sources`} className="workflow-link">Go to Sources →</a>
        </div>

        <div className="workflow-step">
          <div className="step-number">2</div>
          <h3>Extract Atoms</h3>
          <p>Identify and extract key information atoms</p>
          <a href={`/projects/${params.id}/research`} className="workflow-link">Go to Research →</a>
        </div>

        <div className="workflow-step">
          <div className="step-number">3</div>
          <h3>Plan Synthesis</h3>
          <p>Design the synthesis and verification strategy</p>
          <a href={`/projects/${params.id}/graph`} className="workflow-link">Go to Graph →</a>
        </div>

        <div className="workflow-step">
          <div className="step-number">4</div>
          <h3>Execute & Verify</h3>
          <p>Run execution and verify results</p>
          <a href={`/projects/${params.id}/executions`} className="workflow-link">Go to Execution →</a>
        </div>

        <div className="workflow-step">
          <div className="step-number">5</div>
          <h3>Export Results</h3>
          <p>Generate and export final outputs</p>
          <a href={`/projects/${params.id}/handoff`} className="workflow-link">Go to Handoff →</a>
        </div>
      </div>

      <style jsx>{`
        .specgraph-page h1 { margin: 0 0 var(--sg-space-5); }

        .specgraph-workflow {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
          gap: var(--sg-space-4);
        }

        .workflow-step {
          padding: var(--sg-space-4);
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-lg);
          background: var(--sg-surface);
          display: flex;
          flex-direction: column;
          gap: var(--sg-space-2);
        }

        .step-number {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 36px;
          height: 36px;
          background: var(--sg-red-600);
          color: white;
          border-radius: 50%;
          font-weight: var(--sg-weight-bold);
          font-size: var(--sg-type-lg);
        }

        .workflow-step h3 {
          margin: 0;
          font-size: var(--sg-type-base);
        }

        .workflow-step p {
          margin: 0;
          color: var(--sg-text-secondary);
          font-size: var(--sg-type-sm);
        }

        .workflow-link {
          color: var(--sg-red-600);
          text-decoration: none;
          font-size: var(--sg-type-sm);
          font-weight: var(--sg-weight-medium);
          transition: color 0.2s;

          &:hover {
            color: var(--sg-red-700);
          }
        }
      `}</style>
    </div>
  );
}
