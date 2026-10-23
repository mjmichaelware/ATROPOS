/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useEffect, useMemo, FC } from 'react';
import {
  ShieldCheck,
  ShieldAlert,
  Binary,
  Layers,
  Database,
  Search,
  CheckCircle2,
  XCircle,
  Copy,
  Check,
  ArrowRight,
  GitCommit,
  RefreshCw,
  Download,
  Lock,
  FileCode,
  Hash
} from 'lucide-react';

export interface MerkleProofNode {
  hash: string;
  position: 'left' | 'right';
}

export interface CryptographicEvidenceEntry {
  id: string;
  category: 'ast_mutation' | 'contract_proof' | 'approval_checkpoint' | 'test_receipt';
  leafPath: string;
  leafHash: string;
  checkpointRoot: string;
  attestedBy: string;
  timestamp: string;
  payloadSize: number;
  payloadSample: string;
  proofPath: MerkleProofNode[];
}

const SAMPLE_EVIDENCE_RECORDS: CryptographicEvidenceEntry[] = [
  {
    id: 'ev-merkle-8821',
    category: 'ast_mutation',
    leafPath: 'src/main/kotlin/atropos/core/endpoint/OperationEndpoint.kt',
    leafHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    checkpointRoot: '7d5a918a93e8e24c56fd57b0fb76b8a2176dc4c58288eef0120516fc413bf2b6',
    attestedBy: 'Atropos Sovereign Atom Sentinel (ED25519)',
    timestamp: '2026-10-22T20:56:53.000Z',
    payloadSize: 1024,
    payloadSample: 'interface OperationEndpoint {\n  val manifest: OperationManifest\n  fun invoke(params: Map<String, Any>): OperationResult\n}',
    proofPath: [
      { hash: '2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae', position: 'right' },
      { hash: 'fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9', position: 'left' }
    ]
  },
  {
    id: 'ev-contract-9104',
    category: 'contract_proof',
    leafPath: 'scripts/backend-atom-contract-test.sh',
    leafHash: 'b5a2c9b97b0a85233157e8ffb8f722956cf57f7ac81ecf048d0a92be3ab4f2c9',
    checkpointRoot: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
    attestedBy: 'Automated CI Hosted Gatekeeper',
    timestamp: '2026-10-22T20:58:15.000Z',
    payloadSize: 2048,
    payloadSample: 'ATROPOS_BACKEND_ATOM_CONTRACT_OK\nVerification signature validated across 486 hosted test cases.',
    proofPath: [
      { hash: '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', position: 'left' }
    ]
  },
  {
    id: 'ev-approval-3021',
    category: 'approval_checkpoint',
    leafPath: 'governance/quorum-ledger-root.sig',
    leafHash: '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a',
    checkpointRoot: 'ef2d127de37b942baad06145e54b0c619a1f22327b2ebbcfbec78f5564afe39d',
    attestedBy: 'Sovereign Multi-Sig Quorum Authority',
    timestamp: '2026-10-22T21:00:00.000Z',
    payloadSize: 512,
    payloadSample: 'QUORUM_APPROVAL_TOKEN: valid\nThreshold: 66%\nSigned weight: 80/100',
    proofPath: [
      { hash: 'ef2d127de37b942baad06145e54b0c619a1f22327b2ebbcfbec78f5564afe39d', position: 'right' }
    ]
  }
];

export const CryptographicEvidenceExplorer: FC = () => {
  const [entries, setEntries] = useState<CryptographicEvidenceEntry[]>(SAMPLE_EVIDENCE_RECORDS);
  const [selectedEntryId, setSelectedEntryId] = useState<string>(SAMPLE_EVIDENCE_RECORDS[0].id);
  const [searchFilter, setSearchFilter] = useState<string>('');
  const [activeCategory, setActiveCategory] = useState<string>('all');
  const [isVerifying, setIsVerifying] = useState<boolean>(false);
  const [verificationResult, setVerificationResult] = useState<{
    valid: boolean;
    computedRoot: string;
    steps: string[];
  } | null>(null);
  const [copiedHash, setCopiedHash] = useState<string | null>(null);

  const selectedEntry = useMemo(
    () => entries.find((e) => e.id === selectedEntryId) || entries[0],
    [entries, selectedEntryId]
  );

  const filteredEntries = useMemo(() => {
    return entries.filter((e) => {
      const matchesSearch =
        e.id.toLowerCase().includes(searchFilter.toLowerCase()) ||
        e.leafPath.toLowerCase().includes(searchFilter.toLowerCase()) ||
        e.leafHash.toLowerCase().includes(searchFilter.toLowerCase());
      const matchesCategory = activeCategory === 'all' || e.category === activeCategory;
      return matchesSearch && matchesCategory;
    });
  }, [entries, searchFilter, activeCategory]);

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedHash(text);
    setTimeout(() => setCopiedHash(null), 2000);
  };

  // Re-verify Merkle Proof step-by-step
  const handleVerifyMerkleProof = async () => {
    if (!selectedEntry) return;
    setIsVerifying(true);
    setVerificationResult(null);

    const steps: string[] = [];
    let currentHash = selectedEntry.leafHash;
    steps.push(`Leaf hash initialized: ${currentHash.slice(0, 16)}...`);

    const encoder = new TextEncoder();

    try {
      for (let i = 0; i < selectedEntry.proofPath.length; i++) {
        const node = selectedEntry.proofPath[i];
        const combined =
          node.position === 'left'
            ? `${node.hash}${currentHash}`
            : `${currentHash}${node.hash}`;

        const data = encoder.encode(combined);
        const hashBuf = await crypto.subtle.digest('SHA-256', data);
        const hashArr = Array.from(new Uint8Array(hashBuf));
        currentHash = hashArr.map((b) => b.toString(16).padStart(2, '0')).join('');

        steps.push(
          `Step ${i + 1} (${node.position} sibling): H(${node.position === 'left' ? 'Sibling || Current' : 'Current || Sibling'}) -> ${currentHash.slice(0, 16)}...`
        );
      }

      // Check if computed matches checkpoint root
      const valid = currentHash === selectedEntry.checkpointRoot;
      steps.push(
        valid
          ? `Root Match Confirmed: ${currentHash}`
          : `Root Mismatch: Computed ${currentHash} != Attested ${selectedEntry.checkpointRoot}`
      );

      setVerificationResult({
        valid,
        computedRoot: currentHash,
        steps
      });
    } catch {
      setVerificationResult({
        valid: false,
        computedRoot: 'error',
        steps: ['Crypto subtle digest failed in this browser environment.']
      });
    } finally {
      setIsVerifying(false);
    }
  };

  // Export verifiable bundle
  const handleExportBundle = () => {
    if (!selectedEntry) return;
    const bundle = {
      specVersion: '1.2.0',
      evidenceId: selectedEntry.id,
      category: selectedEntry.category,
      leafPath: selectedEntry.leafPath,
      leafHash: selectedEntry.leafHash,
      checkpointRoot: selectedEntry.checkpointRoot,
      attestedBy: selectedEntry.attestedBy,
      timestamp: selectedEntry.timestamp,
      proofPath: selectedEntry.proofPath,
      payloadSample: selectedEntry.payloadSample
    };
    const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `evidence-proof-${selectedEntry.id}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6 rounded-xl border border-sg-neutral-200 dark:border-sg-neutral-800 bg-white/70 dark:bg-sg-neutral-900/60 p-6 backdrop-blur-md shadow-sm">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-emerald-500" />
            <h2 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
              Cryptographic Evidence & Merkle Proof Explorer
            </h2>
          </div>
          <p className="mt-1 text-xs text-sg-neutral-500 dark:text-sg-neutral-400">
            Real-time verification of immutable cryptographic state checkpoints, leaf hashes, and CAS payload integrity.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={handleExportBundle}
            className="flex items-center gap-1.5 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-sg-neutral-100 dark:bg-sg-neutral-800 px-3 py-1.5 text-xs font-medium text-sg-neutral-800 dark:text-sg-neutral-200 hover:bg-sg-neutral-200 dark:hover:bg-sg-neutral-700 transition"
          >
            <Download className="h-3.5 w-3.5" />
            Export Proof Bundle
          </button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-col sm:flex-row items-center gap-3">
        <div className="relative flex-1 w-full">
          <Search className="h-4 w-4 absolute left-3 top-2.5 text-sg-neutral-400" />
          <input
            type="text"
            placeholder="Search evidence ID, leaf path, or SHA-256 hash..."
            value={searchFilter}
            onChange={(e) => setSearchFilter(e.target.value)}
            className="w-full pl-9 pr-4 py-1.5 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 text-xs font-mono text-sg-neutral-800 dark:text-sg-neutral-200"
          />
        </div>

        <div className="flex items-center gap-1.5 w-full sm:w-auto overflow-x-auto pb-1 sm:pb-0">
          {['all', 'ast_mutation', 'contract_proof', 'approval_checkpoint'].map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setActiveCategory(cat)}
              className={`px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition ${
                activeCategory === cat
                  ? 'bg-emerald-600 text-white'
                  : 'bg-sg-neutral-100 dark:bg-sg-neutral-800 text-sg-neutral-600 dark:text-sg-neutral-300 hover:bg-sg-neutral-200 dark:hover:bg-sg-neutral-700'
              }`}
            >
              {cat === 'all' ? 'All Proofs' : cat.replace('_', ' ').toUpperCase()}
            </button>
          ))}
        </div>
      </div>

      {/* Main Two-Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Evidence List */}
        <div className="lg:col-span-5 space-y-2.5 max-h-[520px] overflow-y-auto pr-1">
          {filteredEntries.map((entry) => {
            const isSelected = entry.id === selectedEntry?.id;
            return (
              <div
                key={entry.id}
                onClick={() => {
                  setSelectedEntryId(entry.id);
                  setVerificationResult(null);
                }}
                className={`p-3.5 rounded-xl border cursor-pointer transition text-xs ${
                  isSelected
                    ? 'border-emerald-500 bg-emerald-50/20 dark:bg-emerald-950/20 shadow-sm'
                    : 'border-sg-neutral-200 dark:border-sg-neutral-800 bg-white dark:bg-sg-neutral-900/40 hover:border-sg-neutral-300 dark:hover:border-sg-neutral-700'
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className="font-mono font-bold text-sg-neutral-900 dark:text-sg-neutral-100">
                    {entry.id}
                  </span>
                  <span className="rounded bg-sg-neutral-100 dark:bg-sg-neutral-800 px-1.5 py-0.5 text-[10px] font-mono uppercase text-sg-neutral-600 dark:text-sg-neutral-400">
                    {entry.category.replace('_', ' ')}
                  </span>
                </div>

                <div className="font-mono text-[11px] text-emerald-600 dark:text-emerald-400 truncate mb-1.5">
                  {entry.leafPath}
                </div>

                <div className="flex items-center justify-between text-[10px] text-sg-neutral-400 font-mono">
                  <span>{entry.payloadSize} bytes</span>
                  <span>{new Date(entry.timestamp).toLocaleTimeString()}</span>
                </div>
              </div>
            );
          })}
        </div>

        {/* Right Column: Detailed Proof Inspector */}
        <div className="lg:col-span-7 space-y-4">
          {selectedEntry && (
            <div className="rounded-xl border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950/40 p-4 space-y-4">
              {/* Evidence Metadata */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
                    Leaf Digest & Attestation
                  </span>
                  <button
                    type="button"
                    onClick={() => handleCopy(selectedEntry.leafHash)}
                    className="flex items-center gap-1 text-[11px] font-mono text-sg-neutral-500 hover:text-sg-neutral-800 dark:hover:text-sg-neutral-200"
                  >
                    {copiedHash === selectedEntry.leafHash ? (
                      <Check className="h-3 w-3 text-emerald-500" />
                    ) : (
                      <Copy className="h-3 w-3" />
                    )}
                    {copiedHash === selectedEntry.leafHash ? 'Copied' : 'Copy Hash'}
                  </button>
                </div>

                <div className="p-2.5 rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-white dark:bg-sg-neutral-900 font-mono text-[11px] break-all text-sg-neutral-700 dark:text-sg-neutral-300">
                  <span className="text-sg-neutral-400 select-none">SHA256: </span>
                  {selectedEntry.leafHash}
                </div>

                <div className="flex items-center justify-between text-xs text-sg-neutral-500">
                  <span>Attested by:</span>
                  <span className="font-medium text-sg-neutral-800 dark:text-sg-neutral-200">
                    {selectedEntry.attestedBy}
                  </span>
                </div>
              </div>

              {/* Merkle Proof Path Visualizer */}
              <div className="space-y-2 border-t border-sg-neutral-200 dark:border-sg-neutral-800 pt-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <Layers className="h-4 w-4 text-emerald-500" />
                    <span className="text-xs font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
                      Merkle Verification Proof Path ({selectedEntry.proofPath.length} steps)
                    </span>
                  </div>

                  <button
                    type="button"
                    onClick={handleVerifyMerkleProof}
                    disabled={isVerifying}
                    className="flex items-center gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white px-3 py-1 text-xs font-medium transition disabled:opacity-50"
                  >
                    <RefreshCw className={`h-3 w-3 ${isVerifying ? 'animate-spin' : ''}`} />
                    Verify Proof Chain
                  </button>
                </div>

                <div className="space-y-1.5">
                  {selectedEntry.proofPath.map((step, idx) => (
                    <div
                      key={idx}
                      className="flex items-center justify-between p-2 rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-white dark:bg-sg-neutral-900 text-xs font-mono"
                    >
                      <div className="flex items-center gap-2 truncate">
                        <span className="text-sg-neutral-400 text-[10px]">L{idx + 1}</span>
                        <span className="text-[10px] uppercase font-bold text-indigo-500 px-1 rounded bg-indigo-50 dark:bg-indigo-950">
                          {step.position}
                        </span>
                        <span className="truncate text-sg-neutral-600 dark:text-sg-neutral-300">
                          {step.hash}
                        </span>
                      </div>
                      <Lock className="h-3.5 w-3.5 text-sg-neutral-400 shrink-0" />
                    </div>
                  ))}
                </div>

                {/* Verification Result Output */}
                {verificationResult && (
                  <div
                    className={`p-3 rounded-lg border text-xs space-y-1.5 ${
                      verificationResult.valid
                        ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-900 dark:text-emerald-200'
                        : 'border-rose-500/40 bg-rose-500/10 text-rose-900 dark:text-rose-200'
                    }`}
                  >
                    <div className="flex items-center gap-2 font-bold">
                      {verificationResult.valid ? (
                        <>
                          <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                          <span>PROOF CRYPTOGRAPHICALLY VALID</span>
                        </>
                      ) : (
                        <>
                          <XCircle className="h-4 w-4 text-rose-500" />
                          <span>PROOF VERIFICATION FAILED</span>
                        </>
                      )}
                    </div>
                    <ul className="list-disc pl-4 space-y-0.5 text-[11px] font-mono opacity-90">
                      {verificationResult.steps.map((st, i) => (
                        <li key={i}>{st}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>

              {/* Payload Sample Inspector */}
              <div className="space-y-2 border-t border-sg-neutral-200 dark:border-sg-neutral-800 pt-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <FileCode className="h-4 w-4 text-sg-neutral-500" />
                    <span className="text-xs font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
                      CAS Raw Payload Sample
                    </span>
                  </div>
                  <span className="font-mono text-[10px] text-sg-neutral-400">
                    Content Addressed Storage
                  </span>
                </div>

                <pre className="p-3 rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-white dark:bg-sg-neutral-900 font-mono text-[11px] text-sg-neutral-800 dark:text-sg-neutral-200 overflow-x-auto max-h-36">
                  {selectedEntry.payloadSample}
                </pre>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
