/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  closeTab,
  EMPTY_STORE,
  markClean,
  openTab,
  setContent,
  titleFor,
} from './tabs';

const A = 'src/main.kt';
const B = 'README.md';

describe('editor tab store', () => {
  it('opens a tab and focuses it', () => {
    const store = openTab(EMPTY_STORE, A);
    expect(store.tabs).toHaveLength(1);
    expect(store.activePath).toBe(A);
    expect(store.tabs[0].title).toBe('main.kt');
  });

  it('opening twice focuses rather than duplicates', () => {
    let store = openTab(EMPTY_STORE, A);
    store = openTab(store, B);
    store = openTab(store, A);
    expect(store.tabs).toHaveLength(2);
    expect(store.activePath).toBe(A);
  });

  it('editing marks the tab dirty only when content changed', () => {
    let store = openTab(EMPTY_STORE, A, 'hello');
    store = setContent(store, A, 'hello');
    expect(store.tabs[0].dirty).toBe(false);
    store = setContent(store, A, 'changed');
    expect(store.tabs[0].dirty).toBe(true);
    expect(store.tabs[0].content).toBe('changed');
  });

  it('markClean clears the dirty marker without touching content', () => {
    let store = openTab(EMPTY_STORE, A, 'one');
    store = setContent(store, A, 'two');
    store = markClean(store, A);
    expect(store.tabs[0].dirty).toBe(false);
    expect(store.tabs[0].content).toBe('two');
  });

  it('closing a middle tab focuses its right neighbour', () => {
    let store = EMPTY_STORE;
    for (const path of ['1', '2', '3']) store = openTab(store, path);
    // active is '3'; focus '1' then close it.
    store = { ...store, activePath: '1' };
    store = closeTab(store, '1');
    expect(store.activePath).toBe('2');
  });

  it('closing the last tab leaves no dangling focus', () => {
    let store = openTab(EMPTY_STORE, A);
    store = closeTab(store, A);
    expect(store.tabs).toHaveLength(0);
    expect(store.activePath).toBeUndefined();
  });

  it('closing an unfocused tab keeps current focus', () => {
    let store = openTab(EMPTY_STORE, A);
    store = openTab(store, B); // active = B
    store = closeTab(store, A);
    expect(store.activePath).toBe(B);
  });

  it('title falls back to the full path for bare names', () => {
    expect(titleFor('README.md')).toBe('README.md');
    expect(titleFor('a/b/c.txt')).toBe('c.txt');
    expect(titleFor('')).toBe('');
  });
});
