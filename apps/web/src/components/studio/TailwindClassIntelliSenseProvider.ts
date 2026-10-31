/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM EX15: TAILWIND CSS INTELLISENSE AND CLASS AUTOCOMPLETE
 * Path: apps/web/src/components/studio/TailwindClassIntelliSenseProvider.ts
 *
 * Provides code completion suggestions, color swatches, and utility resolution
 * for Tailwind CSS classes across JSX, TSX, and HTML editor views.
 */

export interface TailwindClassSuggestion {
  className: string;
  category: 'layout' | 'typography' | 'color' | 'spacing' | 'flexbox' | 'grid';
  cssSnippet: string;
  colorPreviewHex?: string;
}

export class TailwindClassIntelliSenseProvider {
  private static COMMON_CLASSES: TailwindClassSuggestion[] = [
    { className: 'flex', category: 'flexbox', cssSnippet: 'display: flex;' },
    { className: 'grid', category: 'grid', cssSnippet: 'display: grid;' },
    { className: 'hidden', category: 'layout', cssSnippet: 'display: none;' },
    { className: 'block', category: 'layout', cssSnippet: 'display: block;' },
    { className: 'items-center', category: 'flexbox', cssSnippet: 'align-items: center;' },
    { className: 'justify-between', category: 'flexbox', cssSnippet: 'justify-content: space-between;' },
    { className: 'justify-center', category: 'flexbox', cssSnippet: 'justify-content: center;' },
    { className: 'p-4', category: 'spacing', cssSnippet: 'padding: 1rem;' },
    { className: 'px-3', category: 'spacing', cssSnippet: 'padding-left: 0.75rem; padding-right: 0.75rem;' },
    { className: 'py-2', category: 'spacing', cssSnippet: 'padding-top: 0.5rem; padding-bottom: 0.5rem;' },
    { className: 'm-2', category: 'spacing', cssSnippet: 'margin: 0.5rem;' },
    { className: 'text-xs', category: 'typography', cssSnippet: 'font-size: 0.75rem; line-height: 1rem;' },
    { className: 'text-sm', category: 'typography', cssSnippet: 'font-size: 0.875rem; line-height: 1.25rem;' },
    { className: 'font-bold', category: 'typography', cssSnippet: 'font-weight: 700;' },
    { className: 'font-mono', category: 'typography', cssSnippet: 'font-family: ui-monospace, SFMono-Regular, Menlo, Monaco;' },
    { className: 'text-cyan-400', category: 'color', cssSnippet: 'color: #22d3ee;', colorPreviewHex: '#22d3ee' },
    { className: 'text-emerald-400', category: 'color', cssSnippet: 'color: #34d399;', colorPreviewHex: '#34d399' },
    { className: 'text-amber-400', category: 'color', cssSnippet: 'color: #fbbf24;', colorPreviewHex: '#fbbf24' },
    { className: 'bg-slate-900', category: 'color', cssSnippet: 'background-color: #0f172a;', colorPreviewHex: '#0f172a' },
    { className: 'bg-slate-950', category: 'color', cssSnippet: 'background-color: #020617;', colorPreviewHex: '#020617' },
    { className: 'border-slate-800', category: 'color', cssSnippet: 'border-color: #1e293b;', colorPreviewHex: '#1e293b' },
    { className: 'rounded-lg', category: 'layout', cssSnippet: 'border-radius: 0.5rem;' },
    { className: 'shadow-2xl', category: 'layout', cssSnippet: 'box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);' },
  ];

  public static getSuggestions(prefix: string): TailwindClassSuggestion[] {
    const query = prefix.trim().toLowerCase();
    if (!query) return this.COMMON_CLASSES.slice(0, 10);
    return this.COMMON_CLASSES.filter((c) => c.className.toLowerCase().includes(query));
  }

  public static resolveColorPreview(className: string): string | undefined {
    const match = this.COMMON_CLASSES.find((c) => c.className === className.trim());
    return match?.colorPreviewHex;
  }
}
