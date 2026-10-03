import { useColorScheme } from 'react-native';

/**
 * Plant-Ride design tokens. Brand: steel navy + furnace orange.
 * Text on orange uses `accent` (#C2410C, 5.2:1 with white); `brandOrange` is decorative only.
 */
const brand = {
  navy: '#0E2A47',
  navyDeep: '#0B1F33',
  navyLight: '#1B4469',
  brandOrange: '#F26B1D',
  brandOrangeLight: '#FFA94D',
};

const light = {
  ...brand,
  dark: false,
  bg: '#F3F5F8',
  surface: '#FFFFFF',
  surface2: '#F8FAFC',
  text: '#0F1B2A',
  text2: '#475569',
  muted: '#64748B',
  border: '#E2E8F0',
  borderStrong: '#CBD5E1',
  accent: '#C2410C',
  accentSoft: '#FFF1E8',
  onAccent: '#FFFFFF',
  ok: '#15803D',
  okSoft: '#E7F6EC',
  warn: '#B45309',
  warnSoft: '#FEF3E2',
  danger: '#B91C1C',
  dangerSoft: '#FDECEC',
  info: '#1D4ED8',
  infoSoft: '#E8EFFF',
  violet: '#6D28D9',
  violetSoft: '#F1EAFE',
  headerFrom: '#1B4469',
  headerTo: '#0B1F33',
  shadow: 'rgba(15, 27, 42, 0.08)',
};

export type Theme = typeof light;

const dark: Theme = {
  ...light,
  dark: true,
  bg: '#0A1521',
  surface: '#101E2E',
  surface2: '#0D1A28',
  text: '#E6EDF5',
  text2: '#B4C2D3',
  muted: '#8597AB',
  border: '#1F3348',
  borderStrong: '#2B4560',
  accent: '#F26B1D',
  accentSoft: 'rgba(242, 107, 29, 0.16)',
  onAccent: '#0B1F33',
  ok: '#4ADE80',
  okSoft: 'rgba(74, 222, 128, 0.14)',
  warn: '#FBBF24',
  warnSoft: 'rgba(251, 191, 36, 0.14)',
  danger: '#F87171',
  dangerSoft: 'rgba(248, 113, 113, 0.14)',
  info: '#7FB0FF',
  infoSoft: 'rgba(127, 176, 255, 0.14)',
  violet: '#C4A5FF',
  violetSoft: 'rgba(196, 165, 255, 0.14)',
  headerFrom: '#12304D',
  headerTo: '#07121E',
  shadow: 'rgba(0, 0, 0, 0.4)',
};

export function useTheme(): Theme {
  return useColorScheme() === 'dark' ? dark : light;
}

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 };
export const radius = { sm: 8, md: 12, lg: 16, pill: 999 };

/** Stable colour per route code so the same line always looks the same. */
const ROUTE_COLOURS = ['#2563EB', '#C2410C', '#7C3AED', '#0F766E', '#BE185D', '#4D7C0F'];
export function routeColour(code: string): string {
  let h = 0;
  for (const ch of code) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return ROUTE_COLOURS[h % ROUTE_COLOURS.length];
}
