import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Grid from '@mui/material/Grid';
import { PokemonSlot } from '../PokemonSlot/PokemonSlot';
import type { TeamBuilderProps } from './TeamBuilder';

export function TeamBuilder({
  team,
  pokemon,
  moves,
  customs,
  showMoves,
  onShowMovesChange,
  onUpdateMember,
  onSaveCustom,
  onRenameTeam,
}: TeamBuilderProps) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
        <TextField
          variant="standard"
          value={team.name}
          onChange={(e) => onRenameTeam(e.target.value)}
          sx={{ '& input': { fontSize: '1.25rem', fontWeight: 600 } }}
        />
        <Typography variant="caption" color="text.secondary">
          {team.members.filter(Boolean).length}/6 filled
        </Typography>
      </Stack>
      <FormControlLabel
        control={<Switch checked={showMoves} onChange={(e) => onShowMovesChange(e.target.checked)} />}
        label="Enable move slots"
      />
      <Grid container spacing={2}>
        {team.members.map((m, i) => (
          <Grid key={i} size={{ xs: 12, md: 6, xl: 4 }}>
            <PokemonSlot
              member={m}
              pokemon={pokemon}
              moves={moves}
              customs={customs}
              showMoves={showMoves}
              onChange={(next) => onUpdateMember(i, next)}
              onSaveCustom={onSaveCustom}
              onClear={() => onUpdateMember(i, null)}
            />
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
