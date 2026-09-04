import { useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import IconButton from '@mui/material/IconButton';
import Fab from '@mui/material/Fab';
import Avatar from '@mui/material/Avatar';
import AvatarGroup from '@mui/material/AvatarGroup';
import Chip from '@mui/material/Chip';
import { AddIcon, CopyIcon } from '../icons/MaterialIcons';
import { NewTeamModal } from '../Modal/NewTeamModal';
import type { TeamsPageProps } from './TeamsPage';

export function TeamsPage({ teams, onSelectTeam, onCreateEmpty, onImport, onRenameTeam, onDuplicateTeam, onSurpriseMe }: TeamsPageProps) {
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');

  function startRename(id: string, name: string) {
    setEditingId(id);
    setEditValue(name);
  }

  function commitRename() {
    if (editingId && editValue.trim()) {
      onRenameTeam(editingId, editValue.trim());
    }
    setEditingId(null);
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Typography variant="h5" sx={{ fontWeight: 600 }}>Your Teams</Typography>
      <Grid container spacing={2}>
        {teams.map((t) => (
          <Grid key={t.id} size={{ xs: 6, sm: 4, md: 3 }}>
            <Card variant="outlined">
              <CardActionArea
                onClick={() => {
                  if (editingId !== t.id) onSelectTeam(t.id);
                }}
              >
                <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  {editingId === t.id ? (
                    <TextField
                      autoFocus
                      size="small"
                      variant="standard"
                      value={editValue}
                      onChange={(e) => setEditValue(e.target.value)}
                      onBlur={commitRename}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') commitRename();
                        if (e.key === 'Escape') setEditingId(null);
                      }}
                      onClick={(e) => e.stopPropagation()}
                    />
                  ) : (
                    <Typography
                      variant="subtitle2"
                      noWrap
                      onDoubleClick={(e) => { e.stopPropagation(); startRename(t.id, t.name); }}
                      title="Double-click to rename"
                    >
                      {t.name}
                    </Typography>
                  )}
                  <Chip label={`${t.members.filter(Boolean).length}/6`} size="small" sx={{ alignSelf: 'flex-start' }} />
                  <AvatarGroup max={6} sx={{ justifyContent: 'center', '& .MuiAvatar-root': { width: 32, height: 32, border: 'none' } }}>
                    {t.members
                      .filter((m): m is NonNullable<typeof m> => m !== null)
                      .map((m, i) => (
                        <Avatar key={m.id ?? i} src={m.spriteUrl ?? undefined} sx={{ bgcolor: 'action.hover' }} />
                      ))}
                  </AvatarGroup>
                </CardContent>
              </CardActionArea>
              <CardActions sx={{ justifyContent: 'flex-end' }}>
                <IconButton
                  size="small"
                  onClick={() => onDuplicateTeam(t.id)}
                  title="Duplicate team"
                  aria-label={`Duplicate ${t.name}`}
                >
                  <CopyIcon fontSize="small" />
                </IconButton>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Fab
        color="primary"
        aria-label="New team"
        onClick={() => setModalOpen(true)}
        sx={{ position: 'fixed', bottom: 80, right: 24 }}
      >
        <AddIcon />
      </Fab>

      <NewTeamModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreateEmpty={onCreateEmpty}
        onImport={onImport}
        onSurpriseMe={onSurpriseMe}
      />
    </Box>
  );
}
