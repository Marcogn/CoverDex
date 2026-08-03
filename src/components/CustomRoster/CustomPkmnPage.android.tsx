import { useState } from 'react';
import { v4 as uuid } from 'uuid';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Avatar from '@mui/material/Avatar';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import { POKEMON_TYPES, PokemonType, TeamMember } from '../../types';
import { TypeBadge } from '../TypeBadge/TypeBadge';
import { AddIcon, DeleteIcon } from '../icons/MaterialIcons';
import type { CustomPkmnPageProps } from './CustomPkmnPage';

export function CustomPkmnPage({ customs, onAdd, onRename, onDelete }: CustomPkmnPageProps) {
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [type1, setType1] = useState<PokemonType>('normal');
  const [type2, setType2] = useState<PokemonType | 'none'>('none');

  function handleSave() {
    if (!name.trim()) return;
    const member: TeamMember = {
      id: uuid(),
      speciesName: name.trim(),
      spriteUrl: null,
      types: [type1, type2 === 'none' ? null : type2],
      moves: [null, null, null, null],
      isCustomSaved: true,
    };
    onAdd(member);
    resetForm();
  }

  function resetForm() {
    setShowForm(false);
    setName('');
    setType1('normal');
    setType2('none');
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, maxWidth: 720 }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>Custom Pokémon</Typography>
        {!showForm && (
          <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={() => setShowForm(true)}>
            Add custom Pokémon
          </Button>
        )}
      </Stack>

      {showForm && (
        <Card variant="outlined">
          <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <TextField autoFocus label="Name" size="small" value={name} onChange={(e) => setName(e.target.value)} />
            <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap' }}>
              <FormControl size="small" sx={{ minWidth: 140 }}>
                <InputLabel>Type 1</InputLabel>
                <Select label="Type 1" value={type1} onChange={(e) => setType1(e.target.value as PokemonType)}>
                  {POKEMON_TYPES.map((t) => (
                    <MenuItem key={t} value={t}>{t}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 140 }}>
                <InputLabel>Type 2</InputLabel>
                <Select label="Type 2" value={type2} onChange={(e) => setType2(e.target.value as PokemonType | 'none')}>
                  <MenuItem value="none">None</MenuItem>
                  {POKEMON_TYPES.map((t) => (
                    <MenuItem key={t} value={t}>{t}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Stack>
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="contained" onClick={handleSave}>Save</Button>
              <Button size="small" onClick={resetForm}>Cancel</Button>
            </Stack>
          </CardContent>
        </Card>
      )}

      {customs.length === 0 && !showForm && (
        <Typography variant="body2" color="text.secondary">No custom Pokémon saved yet.</Typography>
      )}

      <Stack spacing={1.5}>
        {customs.map((c) => (
          <Card key={c.id} variant="outlined">
            <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar src={c.spriteUrl ?? undefined} sx={{ bgcolor: 'action.hover' }} />
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <TextField
                  variant="standard"
                  value={c.speciesName}
                  onChange={(e) => onRename(c.id, e.target.value)}
                  fullWidth
                />
                <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
                  {c.types[0] && <TypeBadge type={c.types[0]} />}
                  {c.types[1] && <TypeBadge type={c.types[1]} />}
                </Stack>
                {c.moves.some(Boolean) && (
                  <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', mt: 0.5 }}>
                    {c.moves.filter(Boolean).map((m) => (
                      <Chip key={m!.id} label={m!.name} size="small" />
                    ))}
                  </Stack>
                )}
              </Box>
              <IconButton size="small" color="error" onClick={() => onDelete(c.id)} aria-label="Delete">
                <DeleteIcon fontSize="small" />
              </IconButton>
            </CardContent>
          </Card>
        ))}
      </Stack>
    </Box>
  );
}
