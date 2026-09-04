import { useEffect, useState } from 'react';
import type { AppSettings } from '../types';

/**
 * Mirrors the light/dark resolution `applyTheme()` (useAppShell.ts) applies
 * to the `dark` DOM class, but as a boolean for MUI's ThemeProvider, which
 * has no way to read a CSS class.
 */
export function useIsDarkMode(theme: AppSettings['theme']): boolean {
  const [systemDark, setSystemDark] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches,
  );

  useEffect(() => {
    if (theme !== 'system') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => setSystemDark(mq.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, [theme]);

  if (theme === 'dark') return true;
  if (theme === 'light') return false;
  return systemDark;
}
