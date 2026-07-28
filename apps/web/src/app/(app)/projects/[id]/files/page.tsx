export default function FilesPage({ params }: { params: { id: string } }) {
  return (
    <div className="files-page">
      <h1>Project Files</h1>
      <div className="file-explorer">
        <div className="empty-state">
          <p>No files yet</p>
        </div>
      </div>
      <style jsx>{`
        .files-page h1 { margin: 0 0 var(--sg-space-4); }
        .file-explorer { border: 1px solid var(--sg-border); border-radius: var(--sg-radius-lg); padding: var(--sg-space-4); }
        .empty-state { padding: var(--sg-space-8); text-align: center; color: var(--sg-text-muted); }
      `}</style>
    </div>
  );
}
