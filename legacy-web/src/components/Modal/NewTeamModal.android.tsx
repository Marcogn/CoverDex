import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import { AddIcon, ImportIcon, ShuffleIcon } from '../icons/MaterialIcons';
import type { NewTeamModalProps } from './NewTeamModal';

export function NewTeamModal({ open, onClose, onCreateEmpty, onImport, onSurpriseMe }: NewTeamModalProps) {
  const { t } = useTranslation();
  const [mode, setMode] = useState<'choose' | 'import'>('choose');
  const [text, setText] = useState('');

  function handleClose() {
    setMode('choose');
    setText('');
    onClose();
  }

  function handleImport() {
    if (text.trim()) {
      onImport(text);
      setMode('choose');
      setText('');
    }
  }

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
      {mode === 'choose' ? (
        <>
          <DialogTitle>{t('teams.newTeam')}</DialogTitle>
          <List sx={{ pt: 0 }}>
            <ListItemButton onClick={() => { onCreateEmpty(); handleClose(); }}>
              <ListItemIcon><AddIcon color="primary" /></ListItemIcon>
              <ListItemText primary={t('teams.createEmpty')} />
            </ListItemButton>
            <ListItemButton onClick={() => setMode('import')}>
              <ListItemIcon><ImportIcon /></ListItemIcon>
              <ListItemText primary={t('teams.importShowdown')} />
            </ListItemButton>
            <ListItemButton onClick={() => { onSurpriseMe(); handleClose(); }}>
              <ListItemIcon><ShuffleIcon /></ListItemIcon>
              <ListItemText primary={t('surpriseMe.button')} />
            </ListItemButton>
          </List>
          <DialogActions>
            <Button onClick={handleClose}>{t('teams.cancel')}</Button>
          </DialogActions>
        </>
      ) : (
        <>
          <DialogTitle>{t('teams.importShowdown')}</DialogTitle>
          <DialogContent>
            <TextField
              autoFocus
              multiline
              fullWidth
              minRows={8}
              placeholder="Paste your Showdown export here"
              value={text}
              onChange={(e) => setText(e.target.value)}
              sx={{ mt: 1, '& textarea': { fontFamily: 'monospace', fontSize: 12 } }}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>{t('teams.cancel')}</Button>
            <Button onClick={handleImport} variant="contained">Import</Button>
          </DialogActions>
        </>
      )}
    </Dialog>
  );
}
