/**
 * ATROPOS Runtime Theme Customization
 * Allows users to override theme colors at runtime and persist preferences
 * Primary color defaults to RED but can be customized to other palettes
 */

export interface ThemeCustomization {
  primaryColor: 'red' | 'blue' | 'green' | 'purple' | string;
  mode: 'light' | 'dark' | 'high-contrast' | 'system';
  customPalette?: Record<string, string>;
}

export interface ColorPalette {
  primary: {
    50: string;
    100: string;
    200: string;
    300: string;
    400: string;
    500: string;
    600: string;
    700: string;
    800: string;
    900: string;
    950: string;
  };
  success: string;
  warning: string;
  danger: string;
  info: string;
}

const STORAGE_KEY = 'atropos-theme-customization';
const DEFAULT_THEME: ThemeCustomization = {
  primaryColor: 'red',
  mode: 'system',
};

// Default color palettes
const colorPalettes: Record<string, ColorPalette> = {
  red: {
    primary: {
      '50': '#fef2f2',
      '100': '#fee2e2',
      '200': '#fecaca',
      '300': '#fca5a5',
      '400': '#f87171',
      '500': '#ef4444',
      '600': '#dc2626',
      '700': '#b91c1c',
      '800': '#991b1b',
      '900': '#7f1d1d',
      '950': '#4c0519',
    },
    success: '#16a34a',
    warning: '#d97706',
    danger: '#dc2626',
    info: '#2563eb',
  },
  blue: {
    primary: {
      '50': '#eff6ff',
      '100': '#dbeafe',
      '200': '#bfdbfe',
      '300': '#93c5fd',
      '400': '#60a5fa',
      '500': '#3b82f6',
      '600': '#2563eb',
      '700': '#1d4ed8',
      '800': '#1e40af',
      '900': '#1e3a8a',
      '950': '#0c2340',
    },
    success: '#16a34a',
    warning: '#d97706',
    danger: '#dc2626',
    info: '#2563eb',
  },
  green: {
    primary: {
      '50': '#f0fdf4',
      '100': '#dcfce7',
      '200': '#bbf7d0',
      '300': '#86efac',
      '400': '#4ade80',
      '500': '#22c55e',
      '600': '#16a34a',
      '700': '#15803d',
      '800': '#166534',
      '900': '#145231',
      '950': '#052e16',
    },
    success: '#16a34a',
    warning: '#d97706',
    danger: '#dc2626',
    info: '#2563eb',
  },
  purple: {
    primary: {
      '50': '#faf5ff',
      '100': '#f3e8ff',
      '200': '#e9d5ff',
      '300': '#d8b4fe',
      '400': '#c084fc',
      '500': '#a855f7',
      '600': '#9333ea',
      '700': '#7e22ce',
      '800': '#6b21a8',
      '900': '#581c87',
      '950': '#3f0f5c',
    },
    success: '#16a34a',
    warning: '#d97706',
    danger: '#dc2626',
    info: '#2563eb',
  },
};

/**
 * Load theme customization from localStorage
 */
export function loadThemeCustomization(): ThemeCustomization {
  if (typeof window === 'undefined') return DEFAULT_THEME;

  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored ? JSON.parse(stored) : DEFAULT_THEME;
  } catch {
    return DEFAULT_THEME;
  }
}

/**
 * Save theme customization to localStorage
 */
export function saveThemeCustomization(theme: ThemeCustomization): void {
  if (typeof window === 'undefined') return;

  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(theme));
  } catch {
    console.warn('Failed to save theme customization');
  }
}

/**
 * Apply theme customization to DOM
 */
export function applyThemeCustomization(theme: ThemeCustomization): void {
  if (typeof document === 'undefined') return;

  const root = document.documentElement;

  // Set theme mode
  root.setAttribute('data-theme', theme.mode);

  // Get color palette (custom or predefined)
  const palette = theme.customPalette
    ? (theme.customPalette as ColorPalette)
    : colorPalettes[theme.primaryColor] || colorPalettes.red;

  // Apply primary color shades
  const primary = palette.primary;
  for (const [shade, hex] of Object.entries(primary)) {
    root.style.setProperty(`--sg-red-${shade}`, hex);
    root.style.setProperty(`--sg-brand-primary-${shade}`, hex);
  }

  // Apply semantic status colors
  root.style.setProperty('--sg-status-success', palette.success);
  root.style.setProperty('--sg-status-warning', palette.warning);
  root.style.setProperty('--sg-status-danger', palette.danger);
  root.style.setProperty('--sg-status-info', palette.info);

  // Update meta theme-color for mobile
  const metaThemeColor = document.querySelector('meta[name="theme-color"]');
  if (metaThemeColor) {
    metaThemeColor.setAttribute('content', palette.primary[600]);
  }
}

/**
 * Initialize theme system on app startup
 */
export function initializeThemeSystem(): void {
  const saved = loadThemeCustomization();
  applyThemeCustomization(saved);

  // Listen for system theme changes
  if (typeof window !== 'undefined' && window.matchMedia) {
    const darkModeQuery = window.matchMedia('(prefers-color-scheme: dark)');
    darkModeQuery.addEventListener('change', () => {
      if (saved.mode === 'system') {
        applyThemeCustomization(saved);
      }
    });
  }
}

/**
 * Get available color palettes
 */
export function getAvailablePalettes(): string[] {
  return Object.keys(colorPalettes);
}

/**
 * Get color palette by name
 */
export function getColorPalette(name: string): ColorPalette | null {
  return colorPalettes[name] || null;
}

/**
 * Hook for React components to manage theme (optional, requires React)
 * Use if available, otherwise use loadThemeCustomization + applyThemeCustomization directly
 */
export function createUseThemeCustomization(React: any) {
  return function useThemeCustomization() {
    const [theme, setTheme] = React.useState<ThemeCustomization>(
      () => loadThemeCustomization()
    );

    const updateTheme = React.useCallback(
      (updates: Partial<ThemeCustomization>) => {
        const newTheme = { ...theme, ...updates };
        setTheme(newTheme);
        saveThemeCustomization(newTheme);
        applyThemeCustomization(newTheme);
      },
      [theme]
    );

    return { theme, updateTheme };
  };
}
