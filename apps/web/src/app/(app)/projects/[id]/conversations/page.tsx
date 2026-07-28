export default function ConversationsPage({ params }: { params: { id: string } }) {
  return (
    <div className="conversations-page">
      <h1>Conversations</h1>
      <div className="empty-state">
        <p>No conversations yet</p>
      </div>
      <style jsx>{`
        .conversations-page h1 { margin: 0 0 var(--sg-space-4); }
        .empty-state { padding: var(--sg-space-8); text-align: center; color: var(--sg-text-muted); }
      `}</style>
    </div>
  );
}
