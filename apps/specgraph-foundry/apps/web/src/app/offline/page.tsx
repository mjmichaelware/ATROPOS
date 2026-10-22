import type { Metadata, Route } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Offline — SpecGraph Foundry",
};

export default function OfflinePage() {
  return (
    <main id="main-content" className="sg-shell sg-offline-page">
      <section className="sg-card" aria-labelledby="offline-title">
        <h1 id="offline-title">You&apos;re offline</h1>
        <p>SpecGraph Foundry could not reach the network. This page works without a connection because it contains no private data.</p>
        <p>Project data, source content, research evidence, plans, and execution records are intentionally never stored for offline use — nothing sensitive is cached on this device.</p>
        <div className="sg-graph-command-group">
          <Link className="sg-button sg-button-primary sg-pressable" href={"/offline" as Route}>
            Retry
          </Link>
          <Link className="sg-button sg-button-secondary sg-pressable" href="/">
            Return to SpecGraph Foundry
          </Link>
        </div>
      </section>
    </main>
  );
}
