/**
 * jsdom does not implement matchMedia. Components that read
 * prefers-reduced-motion/hover/pointer capability (directly, or transitively
 * through shared UI like Tabs) need this stub in tests, matching the
 * existing convention in the graph test suite.
 */
export function stubMatchMedia(matches: (query: string) => boolean = () => false): void {
  window.matchMedia = ((query: string) =>
    ({
      matches: matches(query),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }) as unknown as MediaQueryList) as typeof window.matchMedia;
}
