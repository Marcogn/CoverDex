import { v4 as uuid } from 'uuid';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import { MoveEntry, POKEMON_TYPES, PokemonMove, PokemonType } from '../../types';
import { SearchableDropdown, DropdownOption } from '../SearchableDropdown/SearchableDropdown';
import { DeleteIcon } from '../icons/MaterialIcons';
import type { MoveSlotProps } from './MoveSlot';

export function MoveSlot({ move, moves, onChange }: MoveSlotProps) {
  const options: DropdownOption<MoveEntry>[] = moves.map((m) => ({
    key: 'm-' + m.id,
    label: `${m.displayName} · ${m.type}${m.power ? ` · ${m.power}` : ''}`,
    value: m,
  }));

  function selectMove(_: MoveEntry | null, opt: DropdownOption<MoveEntry> | null) {
    if (!opt) {
      onChange(null);
      return;
    }
    const m = opt.value;
    onChange({
      id: uuid(),
      name: m.displayName,
      type: m.type,
      power: m.power,
      damageClass: m.damageClass,
      isCustom: false,
    });
  }

  return (
    <Box sx={{ bgcolor: 'action.hover', borderRadius: 1, p: 1.5, display: 'flex', flexDirection: 'column', gap: 1 }}>
      <SearchableDropdown<MoveEntry>
        options={options}
        value={null}
        placeholder={move ? move.name : 'Pick or type a move'}
        onChange={selectMove}
      />
      <TextField
        size="small"
        placeholder="Custom move name"
        value={move?.isCustom ? move.name : ''}
        onChange={(e) => {
          const name = e.target.value;
          if (!name) {
            onChange(null);
            return;
          }
          onChange({
            id: move?.id ?? uuid(),
            name,
            type: move?.type ?? 'normal',
            power: move?.power ?? null,
            damageClass: move?.damageClass ?? 'physical',
            isCustom: true,
          });
        }}
      />
      {move?.isCustom && (
        <Stack direction="row" spacing={1}>
          <Select
            size="small"
            value={move.type}
            onChange={(e) => onChange({ ...move, type: e.target.value as PokemonType })}
            sx={{ flex: 1 }}
          >
            {POKEMON_TYPES.map((t) => (
              <MenuItem key={t} value={t}>{t}</MenuItem>
            ))}
          </Select>
          <TextField
            size="small"
            type="number"
            placeholder="Power"
            value={move.power ?? ''}
            onChange={(e) => onChange({ ...move, power: e.target.value ? Number(e.target.value) : null })}
            sx={{ flex: 1 }}
          />
          <Select
            size="small"
            value={move.damageClass}
            onChange={(e) => onChange({ ...move, damageClass: e.target.value as PokemonMove['damageClass'] })}
            sx={{ flex: 1 }}
          >
            <MenuItem value="physical">physical</MenuItem>
            <MenuItem value="special">special</MenuItem>
            <MenuItem value="status">status</MenuItem>
          </Select>
        </Stack>
      )}
      {move && (
        <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="caption" color="text.secondary">
            {move.type} · {move.damageClass} · {move.power ?? '—'}
          </Typography>
          <IconButton size="small" color="error" onClick={() => onChange(null)} aria-label="remove move">
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Stack>
      )}
    </Box>
  );
}
