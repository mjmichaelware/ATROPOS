/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C7: Snippet Autocomplete Suggestion Prioritization.
 *
 * Configures ranking of reusable code snippets within the IntelliSense popup,
 * prioritizing snippets above or below symbol completions according to operator preference.
 *
 * ## Research: Context-Aware AST Completion Ranking
 * Researches promoting snippet items dynamically when typing inside class bodies vs inside method scopes
 * using Tree-Sitter AST cursor location queries.
 */

export type SnippetSuggestionPriority = 'top' | 'bottom' | 'inline' | 'none';

export interface CompletionItemDescriptor {
  label: string;
  kind: 'snippet' | 'symbol' | 'keyword' | 'property';
  insertText: string;
  score?: number;
}

export class SnippetSuggestionPrioritizer {
  static rankCompletions(
    items: CompletionItemDescriptor[],
    priority: SnippetSuggestionPriority = 'bottom'
  ): CompletionItemDescriptor[] {
    if (priority === 'none') {
      return items.filter((item) => item.kind !== 'snippet');
    }

    return [...items].sort((a, b) => {
      if (a.kind === 'snippet' && b.kind !== 'snippet') {
        return priority === 'top' ? -1 : 1;
      }
      if (a.kind !== 'snippet' && b.kind === 'snippet') {
        return priority === 'top' ? 1 : -1;
      }
      return 0;
    });
  }
}
