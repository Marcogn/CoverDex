import SvgIcon, { type SvgIconProps } from '@mui/material/SvgIcon';

/**
 * Small hand-drawn glyphs for the Android nav/action icons, kept dependency-free
 * (no @mui/icons-material) since only a handful are needed. Only ever imported
 * from `*.android.tsx` files, so unreachable from — and excluded from — the web
 * bundle by the same module-graph reasoning as any other Android-only file.
 */

export function TeamsIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </SvgIcon>
  );
}

export function CustomPokemonIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M12 2l2.9 6.26L21.5 9l-5 4.87L17.8 21 12 17.77 6.2 21l1.3-7.13L2.5 9l6.6-.74L12 2z" />
    </SvgIcon>
  );
}

export function SettingsIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <line x1="4" y1="6" x2="20" y2="6" stroke="currentColor" strokeWidth="2" />
      <circle cx="9" cy="6" r="2" fill="currentColor" />
      <line x1="4" y1="12" x2="20" y2="12" stroke="currentColor" strokeWidth="2" />
      <circle cx="15" cy="12" r="2" fill="currentColor" />
      <line x1="4" y1="18" x2="20" y2="18" stroke="currentColor" strokeWidth="2" />
      <circle cx="9" cy="18" r="2" fill="currentColor" />
    </SvgIcon>
  );
}

export function AddIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5z" />
    </SvgIcon>
  );
}

export function ShuffleIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M3 6h3.5l7 12H17M3 18h3.5l2.2-3.8M14 6h3l2 3-2 3h-3M17 4v4M17 16v4" stroke="currentColor" strokeWidth="1.6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </SvgIcon>
  );
}

export function ImportIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M12 3v10m0 0l-4-4m4 4l4-4M5 19h14" stroke="currentColor" strokeWidth="1.8" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </SvgIcon>
  );
}

export function DeleteIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M6 7h12M9 7V5a1 1 0 011-1h4a1 1 0 011 1v2m-7 0v12a1 1 0 001 1h6a1 1 0 001-1V7" stroke="currentColor" strokeWidth="1.8" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </SvgIcon>
  );
}

export function EditIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M4 20h4l10-10-4-4L4 16v4z" stroke="currentColor" strokeWidth="1.6" fill="none" strokeLinejoin="round" />
    </SvgIcon>
  );
}

export function CopyIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <rect x="8" y="8" width="12" height="12" rx="1.5" stroke="currentColor" strokeWidth="1.6" fill="none" />
      <path d="M16 8V5a1 1 0 00-1-1H5a1 1 0 00-1 1v10a1 1 0 001 1h3" stroke="currentColor" strokeWidth="1.6" fill="none" />
    </SvgIcon>
  );
}
