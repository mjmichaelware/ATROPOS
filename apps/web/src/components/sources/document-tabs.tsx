"use client";

import { useState } from "react";
import { Tabs } from "@/components/ui/tabs";
import { DocumentAtoms } from "./document-atoms";
import { DocumentAuthority } from "./document-authority";
import { DocumentChunks } from "./document-chunks";
import { DocumentContent } from "./document-content";
import { DocumentDerivation } from "./document-derivation";
import { DocumentSections } from "./document-sections";
import { ProvenanceViewer } from "./provenance-viewer";
import type { DocumentProvenance, SourceDocument } from "@/lib/sources/schemas";

type DocumentTab = "overview" | "authority" | "derived" | "sections" | "chunks" | "atoms" | "provenance";

export function DocumentTabs({ document, provenance, atoms, hasMoreAtoms, onNextAtoms }: { document: SourceDocument; provenance?: DocumentProvenance; atoms: Array<Record<string, unknown>>; hasMoreAtoms?: boolean; onNextAtoms?: () => void }) {
  const [tab, setTab] = useState<DocumentTab>("overview");
  const sections = Array.isArray(document.sections) ? document.sections as Array<Record<string, unknown>> : [];
  const chunks = Array.isArray(document.chunks) ? document.chunks as Array<Record<string, unknown>> : [];
  return (
    <Tabs
      label="Document inspector"
      value={tab}
      onChange={setTab}
      tabs={[
        { value: "overview", label: "Overview", panel: <DocumentAuthority document={document} provenance={provenance} /> },
        { value: "authority", label: "Authority", panel: <DocumentAuthority document={document} provenance={provenance} /> },
        { value: "derived", label: "Derived text", panel: <><DocumentDerivation derivation={provenance?.provenance?.derivation} /><DocumentContent document={document} /></> },
        { value: "sections", label: "Sections", panel: <DocumentSections sections={sections} /> },
        { value: "chunks", label: "Chunks", panel: <DocumentChunks chunks={chunks} /> },
        { value: "atoms", label: "Atoms", panel: <DocumentAtoms atoms={atoms} hasMore={hasMoreAtoms} onNext={onNextAtoms} /> },
        { value: "provenance", label: "Provenance", panel: <ProvenanceViewer provenance={provenance} /> },
      ]}
    />
  );
}
