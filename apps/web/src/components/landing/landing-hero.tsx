import type { Route } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

function UploadIcon() {
  return (
    <svg viewBox="0 0 32 32" width="28" height="28" fill="none" aria-hidden="true">
      <path d="M16 21V7M16 7l-6 6M16 7l6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6 22v3a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function EvidenceIcon() {
  return (
    <svg viewBox="0 0 32 32" width="28" height="28" fill="none" aria-hidden="true">
      <circle cx="13" cy="13" r="8" stroke="currentColor" strokeWidth="2" />
      <path d="m19 19 7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <path d="m9.5 13 2.5 2.5 5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function GraphIcon() {
  return (
    <svg viewBox="0 0 32 32" width="28" height="28" fill="none" aria-hidden="true">
      <line x1="16" y1="7" x2="8" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <line x1="16" y1="7" x2="24" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <line x1="8" y1="23" x2="24" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <circle cx="16" cy="7" r="3" fill="currentColor" />
      <circle cx="8" cy="23" r="3" fill="currentColor" />
      <circle cx="24" cy="23" r="3" fill="currentColor" />
    </svg>
  );
}

function VerifiedIcon() {
  return (
    <svg viewBox="0 0 32 32" width="28" height="28" fill="none" aria-hidden="true">
      <path d="M16 4l9 4v7c0 7-4.5 11-9 13-4.5-2-9-6-9-13V8l9-4Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <path d="m11.5 16 3 3 6-6.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

const FEATURES = [
  {
    title: "Upload & understand",
    body: "Bring in your source documents. SpecGraph extracts and organizes everything into addressable, provenance-tracked atoms — nothing is summarized away.",
    Icon: UploadIcon,
  },
  {
    title: "Research with evidence",
    body: "Every open question is tracked to closure. Claims are backed by traceable evidence and reviewed independently before they ever count as resolved.",
    Icon: EvidenceIcon,
  },
  {
    title: "Plan & visualize",
    body: "Explore authority and execution graphs side by side. Catch cycles, gaps, and blockers before they become expensive problems.",
    Icon: GraphIcon,
  },
  {
    title: "Verified handoff",
    body: "Generate signed, checksummed export packages and follow execution through to independent, tamper-evident verification.",
    Icon: VerifiedIcon,
  },
];

export function LandingHero() {
  return (
    <div className="sg-landing">
      <div className="sg-landing-ambient" aria-hidden="true">
        <span className="sg-splash-blob sg-splash-blob-a" />
        <span className="sg-splash-blob sg-splash-blob-b" />
      </div>
      <section className="sg-landing-hero sg-reveal" aria-labelledby="landing-title">
        <p className="sg-micro-label">SpecGraph Foundry</p>
        <h1 id="landing-title" className="sg-landing-title">
          SpecGraph
        </h1>
        <p className="sg-landing-tagline">Turn source documents into verified, execution-ready plans.</p>
        <p className="sg-landing-lede">
          SpecGraph builds a living authority graph from your source material — researched, cross-checked, and
          independently verified at every step, so nothing ships without proof.
        </p>
        <div className="sg-landing-actions">
          <Button asChild>
            <Link href={"/auth/sign-in" as Route}>Sign in</Link>
          </Button>
          <Button asChild variant="secondary">
            <Link href={"/auth/sign-up" as Route}>Create account</Link>
          </Button>
        </div>
      </section>

      <section className="sg-grid" aria-label="What SpecGraph does">
        {FEATURES.map(({ title, body, Icon }) => (
          <Card key={title} className="sg-landing-feature">
            <span className="sg-landing-feature-icon" aria-hidden="true">
              <Icon />
            </span>
            <h2>{title}</h2>
            <p>{body}</p>
          </Card>
        ))}
      </section>
    </div>
  );
}
