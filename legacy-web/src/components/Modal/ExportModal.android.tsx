import { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import { exportTeamToShowdown } from '../../utils/showdownParser';
import type { ExportModalProps } from './ExportModal';

export function ExportModal({ open, onClose, team }: ExportModalProps) {
  const [copied, setCopied] = useState(false);
  const exported = exportTeamToShowdown(team.members);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(exported);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  function handleDownload() {
    const safeName = (team.name || 'team').replace(/[/\\:*?"<>|]/g, '_');
    const blob = new Blob([exported], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${safeName}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Export Team</DialogTitle>
      <DialogContent>
        <TextField
          fullWidth
          multiline
          minRows={10}
          slotProps={{ input: { readOnly: true } }}
          value={exported}
          sx={{ mt: 1, '& textarea': { fontFamily: 'monospace', fontSize: 12 } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button onClick={handleDownload}>Download .txt</Button>
        <Button onClick={handleCopy} variant="contained">
          {copied ? 'Copied!' : 'Copy to clipboard'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
