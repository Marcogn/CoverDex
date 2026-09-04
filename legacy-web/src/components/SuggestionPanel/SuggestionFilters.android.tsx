import { useTranslation } from 'react-i18next';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import ToggleButton from '@mui/material/ToggleButton';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import { POKEMON_TYPES, PokemonType } from '../../types';
import { TypeBadge } from '../TypeBadge/TypeBadge';
import { GEN_LABELS, GenerationFilter } from './SuggestionFilters';
import type { SuggestionFiltersProps } from './SuggestionFilters';

export function SuggestionFilters({ filters, onChange, onRandomize }: SuggestionFiltersProps) {
  const { t } = useTranslation();
  const toggleType = (tp: PokemonType) => {
    const next = filters.types.includes(tp)
      ? filters.types.filter((x) => x !== tp)
      : [...filters.types, tp];
    onChange({ ...filters, types: next });
  };

  return (
    <Paper variant="outlined" sx={{ p: 1.5, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <Select
            value={filters.generation}
            onChange={(e) => onChange({ ...filters, generation: e.target.value as GenerationFilter })}
          >
            {(Object.keys(GEN_LABELS) as GenerationFilter[]).map((g) => (
              <MenuItem key={g} value={g}>{g === 'all' ? t('suggestions.allGenerations') : GEN_LABELS[g]}</MenuItem>
            ))}
          </Select>
        </FormControl>

        <ToggleButtonGroup
          size="small"
          exclusive
          value={filters.mode}
          onChange={(_e, mode) => mode && onChange({ ...filters, mode })}
        >
          <ToggleButton value="best">{t('suggestions.bestCoverage')}</ToggleButton>
          <ToggleButton value="random">{t('suggestions.random')}</ToggleButton>
        </ToggleButtonGroup>

        {filters.mode === 'random' && (
          <Button size="small" onClick={onRandomize}>{t('suggestions.randomizeAgain')}</Button>
        )}
      </Stack>

      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>
        {POKEMON_TYPES.map((tp) => {
          const selected = filters.types.includes(tp);
          return (
            <Chip
              key={tp}
              icon={<TypeBadge type={tp} />}
              label={tp}
              size="small"
              onClick={() => toggleType(tp)}
              color={selected ? 'primary' : 'default'}
              variant={selected ? 'filled' : 'outlined'}
            />
          );
        })}
      </Stack>
    </Paper>
  );
}
