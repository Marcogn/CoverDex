import { createTheme, type Theme } from '@mui/material/styles';

const ACCENT = '#7c5cff';

/**
 * Material 3-flavored theme for the Android build only. `Roboto` is listed
 * first in the font stack but never web-loaded — Android ships Roboto as
 * the OS system font, so the WebView resolves it natively with zero extra
 * bytes; browsers without it fall back to the rest of the stack.
 */
export function createAppMuiTheme(darkMode: boolean): Theme {
  return createTheme({
    palette: {
      mode: darkMode ? 'dark' : 'light',
      primary: { main: ACCENT },
      background: darkMode
        ? { default: '#1a1a2e', paper: '#23233a' }
        : { default: '#ffffff', paper: '#f8fafc' },
    },
    shape: { borderRadius: 12 },
    typography: {
      fontFamily: [
        'Roboto',
        '-apple-system',
        'BlinkMacSystemFont',
        'Segoe UI',
        'Helvetica',
        'Arial',
        'sans-serif',
      ].join(','),
    },
    components: {
      MuiAppBar: { defaultProps: { elevation: 0 } },
      MuiButton: { defaultProps: { disableElevation: true } },
    },
  });
}
