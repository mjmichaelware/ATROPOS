import { describe, expect, it } from 'vitest';
import { WebMergeArchitecture } from './web-merge';

describe('WebMergeArchitecture', () => {
  it('keeps one canonical runtime and isolates SpecGraph under developer tools', () => {
    expect(WebMergeArchitecture.isCanonicalRuntimeRoot('apps/web')).toBe(true);
    expect(WebMergeArchitecture.isCanonicalRuntimeRoot('apps/specgraph-foundry/apps/web')).toBe(false);
    expect(WebMergeArchitecture.isDeveloperToolsRoute('/developer/specgraph/p1')).toBe(true);
    expect(WebMergeArchitecture.isDeveloperToolsRoute('/projects')).toBe(false);
  });
});
