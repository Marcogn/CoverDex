import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { KNOWN_ABILITIES_WITH_EFFECTS, normalizeAbilityName, ABILITY_EFFECTS } from '../../data/abilityEffects';
import type { AbilityDropdownProps } from './AbilityDropdown';

function effectKind(ability: string): 'coverage' | 'display-only' | null {
  const slug = normalizeAbilityName(ability);
  const effects = ABILITY_EFFECTS[slug];
  if (!effects) return null;
  if (effects.some((e) => e.kind === 'badge-only')) return 'display-only';
  return 'coverage';
}

/**
 * Android version of AbilityDropdown: MUI Autocomplete in freeSolo mode, so
 * ROM-hack/custom ability names can still be typed even though they aren't
 * in KNOWN_ABILITIES_WITH_EFFECTS.
 */
export function AbilityDropdown({ value, onChange, placeholder }: AbilityDropdownProps) {
  const { t } = useTranslation();
  const options = useMemo(() => [...KNOWN_ABILITIES_WITH_EFFECTS], []);

  return (
    <Autocomplete
      freeSolo
      options={options}
      value={value || null}
      inputValue={value}
      onInputChange={(_e, newValue) => onChange(newValue)}
      onChange={(_e, newValue) => onChange(newValue ?? '')}
      renderOption={(props, option) => {
        const effect = effectKind(option);
        return (
          <Box component="li" {...props} key={option} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2" sx={{ textTransform: 'capitalize', flex: 1 }}>{option}</Typography>
            {effect === 'coverage' && <Chip label={t('slot.coverageEffect')} size="small" color="success" />}
            {effect === 'display-only' && <Chip label={t('slot.displayOnly')} size="small" color="secondary" />}
          </Box>
        );
      }}
      renderInput={(params) => (
        <TextField {...params} size="small" placeholder={placeholder ?? t('common.searchPlaceholder')} />
      )}
    />
  );
}
