/**
 * ATROPOS Design Tokens
 * Shared tokens for web, CLI, and mobile with RED as primary theme color
 */

export * from './customization/runtimeThemeMixin';

// Re-export tokens.json for programmatic access if needed
import tokens from './tokens.json';
export { tokens };

/**
 * Preset color palettes
 */
export const colorPalettes = {
  red: {
    name: 'Red (Default)',
    primary: '600',
    hex: '#dc2626',
  },
  blue: {
    name: 'Blue',
    primary: '600',
    hex: '#2563eb',
  },
  green: {
    name: 'Green',
    primary: '600',
    hex: '#16a34a',
  },
  purple: {
    name: 'Purple',
    primary: '600',
    hex: '#9333ea',
  },
};

/**
 * Theme modes
 */
export const themeModes = ['light', 'dark', 'high-contrast', 'system'] as const;

/**
 * Get CSS variable name for a token
 * Example: getTokenVar('red', '600') => '--sg-red-600'
 */
export function getTokenVar(tokenPath: string, shade?: string): string {
  const base = `--sg-${tokenPath}`;
  return shade ? `${base}-${shade}` : base;
}

/**
 * Get CSS custom property value
 * Example: getTokenValue('--sg-red-600') => '#dc2626'
 */
export function getTokenValue(varName: string): string {
  if (typeof window === 'undefined') return '';
  return getComputedStyle(document.documentElement).getPropertyValue(varName).trim();
}

/**
 * Predefined semantic token paths
 */
export const semanticTokens = {
  color: {
    brand: 'brand-primary',
    surface: 'surface-canvas',
    text: 'text-primary',
    border: 'border',
    success: 'status-success',
    warning: 'status-warning',
    danger: 'status-danger',
  },
  spacing: {
    xs: 'space-1',
    sm: 'space-2',
    md: 'space-4',
    lg: 'space-6',
    xl: 'space-8',
  },
  motion: {
    fast: 'motion-duration-fast',
    normal: 'motion-duration-normal',
    slow: 'motion-duration-slow',
  },
};

/**
 * Utility to apply custom colors globally
 */
export function applyCustomColors(colorMap: Record<string, string>): void {
  if (typeof document === 'undefined') return;

  const root = document.documentElement;
  for (const [token, value] of Object.entries(colorMap)) {
    root.style.setProperty(`--sg-${token}`, value);
  }
}

export default tokens;
