'use client';

import React, { useState, useRef, useEffect, useMemo } from 'react';
import {
  Save,
  RotateCcw,
  Search,
  Check,
  Code2,
  FileCode,
  Sparkles,
  Copy,
  ChevronRight,
  Maximize2,
  Minimize2
} from 'lucide-react';

import { EditorGroupTabController, type EditorTabModel, type SplitDirection } from './EditorGroupTabController';

export { EditorGroupTabController };
export type { EditorTabModel, SplitDirection };

export interface MonacoCodeEditorProps {
  filePath?: string;
  initialContent?: string;
  value?: string;
  filename?: string;
  language?: string;
  readOnly?: boolean;
  isDirty?: boolean;
  onSave?: (path: string, content: string) => Promise<boolean> | boolean | void;
  onChange?: (val: string) => void;
  onDirtyChange?: (isDirty: boolean) => void;
}

export function MonacoCodeEditor({
  filePath = '',
  initialContent,
  value,
  filename,
  language = 'kotlin',
  readOnly = false,
  isDirty: externalIsDirty,
  onSave,
  onChange,
  onDirtyChange
}: MonacoCodeEditorProps) {
  const effectiveInitial = value ?? initialContent ?? '';
  const [content, setContent] = useState<string>(effectiveInitial);
  const [originalContent, setOriginalContent] = useState<string>(effectiveInitial);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isSearching, setIsSearching] = useState<boolean>(false);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [saveSuccess, setSaveSuccess] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const lineGutterRef = useRef<HTMLDivElement>(null);

  const isDirty = content !== originalContent;

  useEffect(() => {
    onDirtyChange?.(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => {
    const nextVal = value ?? initialContent ?? '';
    setContent(nextVal);
    setOriginalContent(nextVal);
  }, [initialContent, value, filePath]);

  const lines = useMemo(() => content.split('\n'), [content]);

  // Handle synchronized scrolling between gutter and textarea
  const handleScroll = (e: React.UIEvent<HTMLTextAreaElement>) => {
    if (lineGutterRef.current) {
      lineGutterRef.current.scrollTop = e.currentTarget.scrollTop;
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Cmd/Ctrl + S to save
    if ((e.metaKey || e.ctrlKey) && e.key === 's') {
      e.preventDefault();
      handleSave();
      return;
    }

    // Cmd/Ctrl + F to find
    if ((e.metaKey || e.ctrlKey) && e.key === 'f') {
      e.preventDefault();
      setIsSearching((prev) => !prev);
      return;
    }

    // Tab key indent
    if (e.key === 'Tab') {
      e.preventDefault();
      const textarea = textareaRef.current;
      if (!textarea) return;

      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const newContent = content.substring(0, start) + '    ' + content.substring(end);
      setContent(newContent);

      setTimeout(() => {
        textarea.selectionStart = textarea.selectionEnd = start + 4;
      }, 0);
    }
  };

  const handleSave = async () => {
    if (readOnly || !isDirty) return;
    setIsSaving(true);
    try {
      if (onSave) {
        await onSave(filePath, content);
      }
      setOriginalContent(content);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
    } finally {
      setIsSaving(false);
    }
  };

  const handleRevert = () => {
    setContent(originalContent);
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(content);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div
      className={`flex flex-col bg-slate-950 border border-slate-800 rounded-xl overflow-hidden font-mono text-xs shadow-2xl transition-all ${
        isFullscreen ? 'fixed inset-4 z-50' : 'h-full w-full'
      }`}
    >
      {/* Editor Header Bar */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-slate-900 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <FileCode className="h-4 w-4 text-cyan-400" />
          <span className="font-bold text-white text-xs">{filePath}</span>
          {isDirty && (
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" title="Unsaved changes" />
          )}
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700">
            {language}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {/* Find in File Toggle */}
          <button
            onClick={() => setIsSearching((prev) => !prev)}
            title="Find in Buffer (Cmd+F)"
            className={`p-1.5 rounded-lg border transition-colors ${
              isSearching
                ? 'bg-cyan-500/20 border-cyan-400 text-cyan-300'
                : 'border-slate-800 bg-slate-900 text-slate-400 hover:text-white'
            }`}
          >
            <Search className="h-3.5 w-3.5" />
          </button>

          {/* Copy Buffer */}
          <button
            onClick={handleCopy}
            title="Copy Buffer"
            className="p-1.5 rounded-lg border border-slate-800 bg-slate-900 text-slate-400 hover:text-white transition-colors"
          >
            {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
          </button>

          {/* Revert Changes */}
          {isDirty && (
            <button
              onClick={handleRevert}
              title="Revert Unsaved Changes"
              className="p-1.5 rounded-lg border border-slate-800 bg-slate-900 text-slate-400 hover:text-rose-400 transition-colors"
            >
              <RotateCcw className="h-3.5 w-3.5" />
            </button>
          )}

          {/* Save Button */}
          {!readOnly && (
            <button
              onClick={handleSave}
              disabled={!isDirty || isSaving}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-bold text-xs transition-colors ${
                isDirty
                  ? 'bg-cyan-400 hover:bg-cyan-300 text-black shadow-md shadow-cyan-500/20'
                  : 'bg-slate-800 text-slate-500 cursor-not-allowed border border-slate-700'
              }`}
            >
              {saveSuccess ? (
                <>
                  <Check className="h-3.5 w-3.5" />
                  <span>Saved</span>
                </>
              ) : (
                <>
                  <Save className="h-3.5 w-3.5" />
                  <span>{isSaving ? 'Saving...' : 'Save (Cmd+S)'}</span>
                </>
              )}
            </button>
          )}

          {/* Fullscreen Toggle */}
          <button
            onClick={() => setIsFullscreen((prev) => !prev)}
            title={isFullscreen ? 'Exit Fullscreen' : 'Fullscreen'}
            className="p-1.5 rounded-lg border border-slate-800 bg-slate-900 text-slate-400 hover:text-white transition-colors"
          >
            {isFullscreen ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
          </button>
        </div>
      </div>

      {/* Find Search Floating Toolbar */}
      {isSearching && (
        <div className="flex items-center gap-2 px-4 py-2 bg-slate-900/90 border-b border-slate-800 text-xs">
          <Search className="h-3.5 w-3.5 text-cyan-400 shrink-0" />
          <input
            autoFocus
            type="text"
            placeholder="Search in file..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="flex-1 bg-transparent outline-none text-white placeholder-slate-500"
          />
          <span className="text-[10px] text-slate-500 font-mono">
            {searchQuery ? `${lines.filter((l) => l.includes(searchQuery)).length} matches` : 'ESC to close'}
          </span>
          <button
            onClick={() => {
              setIsSearching(false);
              setSearchQuery('');
            }}
            className="text-slate-400 hover:text-white text-xs ml-2"
          >
            ✕
          </button>
        </div>
      )}

      {/* Editor Body: Gutter + Monospace Textarea */}
      <div className="relative flex-1 flex overflow-hidden">
        {/* Line Numbers Gutter */}
        <div
          ref={lineGutterRef}
          className="w-12 py-3 bg-slate-900/50 border-r border-slate-800/80 text-right pr-3 select-none text-slate-600 font-mono text-xs overflow-hidden leading-6"
        >
          {lines.map((_, i) => (
            <div key={i}>{i + 1}</div>
          ))}
        </div>

        {/* Text Area Body */}
        <textarea
          ref={textareaRef}
          value={content}
          onChange={(e) => {
            setContent(e.target.value);
            onChange?.(e.target.value);
          }}
          onScroll={handleScroll}
          onKeyDown={handleKeyDown}
          readOnly={readOnly}
          spellCheck={false}
          className="flex-1 p-3 bg-transparent text-slate-200 font-mono text-xs leading-6 resize-none outline-none overflow-auto whitespace-pre tab-4 select-text"
        />
      </div>

      {/* Editor Footer Status Bar */}
      <div className="flex items-center justify-between px-4 py-1.5 bg-slate-900 border-t border-slate-800 text-[11px] text-slate-400 font-mono">
        <div className="flex items-center gap-3">
          <span>{lines.length} lines</span>
          <span>{content.length} characters</span>
          <span>UTF-8</span>
        </div>

        <div className="flex items-center gap-3">
          <span>Spaces: 4</span>
          <span className="text-cyan-400 font-bold">{isDirty ? '● MODIFIED' : 'CLEAN'}</span>
        </div>
      </div>
    </div>
  );
}
