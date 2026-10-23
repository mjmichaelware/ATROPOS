'use client';

import React, { useState } from 'react';
import {
  Code2,
  ChevronRight,
  ChevronDown,
  Layers,
  Sparkles,
  Search,
  CheckCircle2,
  AlertTriangle,
  Info
} from 'lucide-react';

export interface AstNode {
  id: string;
  kind: string;
  name?: string;
  type?: string;
  line: number;
  column: number;
  children?: AstNode[];
  isError?: boolean;
  repairSuggestion?: string;
}

const SAMPLE_AST: AstNode = {
  id: 'node-0',
  kind: 'SourceFile',
  name: 'src/main/kotlin/atropos/core/Router.kt',
  line: 1,
  column: 1,
  children: [
    {
      id: 'node-1',
      kind: 'PackageDeclaration',
      name: 'atropos.core',
      line: 1,
      column: 1
    },
    {
      id: 'node-2',
      kind: 'ImportList',
      line: 3,
      column: 1,
      children: [
        { id: 'node-3', kind: 'ImportDirective', name: 'atropos.bridge.EndpointRegistry', line: 3, column: 1 },
        { id: 'node-4', kind: 'ImportDirective', name: 'atropos.core.policy.BoundedAgencyGate', line: 4, column: 1 }
      ]
    },
    {
      id: 'node-5',
      kind: 'ClassDeclaration',
      name: 'Router',
      type: 'class',
      line: 6,
      column: 1,
      children: [
        {
          id: 'node-6',
          kind: 'PrimaryConstructor',
          line: 6,
          column: 14,
          children: [
            { id: 'node-7', kind: 'ValueParameter', name: 'registry', type: 'EndpointRegistry', line: 6, column: 22 }
          ]
        },
        {
          id: 'node-8',
          kind: 'FunctionDeclaration',
          name: 'dispatch',
          type: 'Response',
          line: 8,
          column: 5,
          children: [
            { id: 'node-9', kind: 'ValueParameter', name: 'route', type: 'String', line: 8, column: 18 },
            {
              id: 'node-10',
              kind: 'CallExpression',
              name: 'registry.resolve(route)',
              line: 8,
              column: 38
            }
          ]
        }
      ]
    }
  ]
};

export function AstVisualizer() {
  const [root] = useState<AstNode>(SAMPLE_AST);
  const [selectedNode, setSelectedNode] = useState<AstNode | null>(SAMPLE_AST);
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(
    new Set(['node-0', 'node-2', 'node-5', 'node-8'])
  );

  const toggleExpand = (id: string) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <div className="flex flex-col lg:flex-row h-full w-full bg-slate-950 text-slate-100 border border-slate-800 rounded-xl overflow-hidden font-mono text-xs divide-y lg:divide-y-0 lg:divide-x divide-slate-800">
      {/* Left: AST Tree View */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="flex items-center justify-between px-4 py-2.5 bg-slate-900 border-b border-slate-800">
          <span className="font-bold text-white flex items-center gap-2">
            <Code2 className="h-4 w-4 text-cyan-400" />
            SYNTAX TREE GRAPH (AST)
          </span>
          <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-bold">
            0 SYNTAX ERRORS
          </span>
        </div>

        <div className="flex-1 p-4 overflow-y-auto space-y-1">
          <AstTreeRow
            node={root}
            depth={0}
            selectedId={selectedNode?.id}
            expandedNodes={expandedNodes}
            onToggle={toggleExpand}
            onSelect={setSelectedNode}
          />
        </div>
      </div>

      {/* Right: Node Inspector Drawer */}
      <div className="w-full lg:w-80 p-5 bg-slate-900/50 flex flex-col space-y-4 shrink-0 overflow-y-auto">
        <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
          <Info className="h-4 w-4 text-cyan-400" />
          <span className="font-bold text-white uppercase text-xs">Node Inspector</span>
        </div>

        {selectedNode ? (
          <div className="space-y-3">
            <div className="space-y-1">
              <span className="text-[10px] text-slate-500">AST KIND</span>
              <p className="text-cyan-400 font-bold text-sm">{selectedNode.kind}</p>
            </div>

            {selectedNode.name && (
              <div className="space-y-1">
                <span className="text-[10px] text-slate-500">SYMBOL IDENTIFIER</span>
                <p className="text-white font-bold">{selectedNode.name}</p>
              </div>
            )}

            {selectedNode.type && (
              <div className="space-y-1">
                <span className="text-[10px] text-slate-500">INFERRED TYPE</span>
                <p className="text-purple-300 font-bold">{selectedNode.type}</p>
              </div>
            )}

            <div className="space-y-1">
              <span className="text-[10px] text-slate-500">SOURCE POSITION</span>
              <p className="text-slate-300">
                Line {selectedNode.line}, Column {selectedNode.column}
              </p>
            </div>

            <div className="space-y-1">
              <span className="text-[10px] text-slate-500">CHILD SUBTREES</span>
              <p className="text-slate-300">{selectedNode.children?.length ?? 0} direct children</p>
            </div>
          </div>
        ) : (
          <p className="text-slate-500 text-center py-8">Select a node from the syntax tree to inspect.</p>
        )}
      </div>
    </div>
  );
}

function AstTreeRow({
  node,
  depth,
  selectedId,
  expandedNodes,
  onToggle,
  onSelect
}: {
  node: AstNode;
  depth: number;
  selectedId?: string;
  expandedNodes: Set<string>;
  onToggle: (id: string) => void;
  onSelect: (node: AstNode) => void;
}) {
  const hasChildren = node.children && node.children.length > 0;
  const isExpanded = expandedNodes.has(node.id);
  const isSelected = selectedId === node.id;

  return (
    <div>
      <div
        onClick={() => {
          onSelect(node);
          if (hasChildren) onToggle(node.id);
        }}
        style={{ paddingLeft: `${depth * 16 + 6}px` }}
        className={`flex items-center justify-between py-1 px-2 rounded-lg cursor-pointer transition-colors ${
          isSelected
            ? 'bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/40'
            : 'hover:bg-slate-900 text-slate-300 hover:text-white'
        }`}
      >
        <div className="flex items-center gap-1.5 min-w-0">
          {hasChildren ? (
            isExpanded ? (
              <ChevronDown className="h-3.5 w-3.5 text-slate-500 shrink-0" />
            ) : (
              <ChevronRight className="h-3.5 w-3.5 text-slate-500 shrink-0" />
            )
          ) : (
            <div className="w-3.5 shrink-0" />
          )}

          <span className="text-cyan-400 font-bold">{node.kind}</span>
          {node.name && <span className="text-white truncate font-normal">{node.name}</span>}
        </div>

        {node.type && (
          <span className="text-[10px] px-1.5 py-0.2 rounded bg-purple-950/60 border border-purple-800 text-purple-300 ml-2 shrink-0">
            {node.type}
          </span>
        )}
      </div>

      {hasChildren && isExpanded && (
        <div className="space-y-0.5">
          {node.children!.map((child) => (
            <AstTreeRow
              key={child.id}
              node={child}
              depth={depth + 1}
              selectedId={selectedId}
              expandedNodes={expandedNodes}
              onToggle={onToggle}
              onSelect={onSelect}
            />
          ))}
        </div>
      )}
    </div>
  );
}
