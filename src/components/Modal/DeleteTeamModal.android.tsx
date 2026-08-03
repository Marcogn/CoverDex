import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import type { DeleteTeamModalProps } from './DeleteTeamModal';

export function DeleteTeamModal({ open, onClose, onConfirm, teamName }: DeleteTeamModalProps) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Delete Team</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Are you sure you want to delete <strong>{teamName}</strong>? This cannot be undone.
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="error" variant="contained" onClick={() => { onConfirm(); onClose(); }}>
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  );
}
