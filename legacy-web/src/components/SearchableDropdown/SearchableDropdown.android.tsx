import { useMemo, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import Autocomplete, { createFilterOptions } from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { DropdownOption } from './SearchableDropdown';

interface Props<T> {
  options: DropdownOption<T>[];
  value: T | null;
  placeholder?: string;
  onChange: (v: T | null, opt: DropdownOption<T> | null) => void;
  renderOption?: (opt: DropdownOption<T>) => ReactNode;
  emptyHint?: string;
  dropdownClassName?: string;
  useFixedPosition?: boolean;
}

/**
 * Android presentation of the shared searchable-dropdown pattern (see
 * CLAUDE.md), backed by MUI Autocomplete. Used automatically wherever the
 * web build imports SearchableDropdown — Pokémon picker, move picker,
 * anchor picker — since it's resolved per the platform-file convention.
 * `dropdownClassName`/`useFixedPosition` are web-only concerns (manual
 * overlay positioning) that Autocomplete's own popper handles natively, so
 * they're accepted but unused here.
 */
export function SearchableDropdown<T>({
  options,
  value,
  placeholder = 'Search…',
  onChange,
  renderOption,
  emptyHint = 'No matches',
}: Props<T>) {
  const { t } = useTranslation();
  const selected = useMemo(() => options.find((o) => o.value === value) ?? null, [options, value]);

  // CLAUDE.md "Searchable Dropdowns": no results shown until the user has
  // typed at least one character.
  const filter = useMemo(
    () => createFilterOptions<DropdownOption<T>>({ stringify: (o) => o.label }),
    [],
  );

  return (
    <Autocomplete<DropdownOption<T>, false, false, false>
      options={options}
      value={selected}
      getOptionLabel={(o) => o.label}
      isOptionEqualToValue={(a, b) => a.key === b.key}
      onChange={(_e, opt) => onChange(opt ? opt.value : null, opt)}
      filterOptions={(opts, state) => (state.inputValue.trim() ? filter(opts, state) : [])}
      noOptionsText={t('common.searchPlaceholder')}
      renderOption={(props, option) => (
        <Box component="li" {...props} key={option.key} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {renderOption ? (
            renderOption(option)
          ) : (
            <>
              {option.spriteUrl && (
                <Avatar src={option.spriteUrl} variant="square" sx={{ width: 24, height: 24, bgcolor: 'transparent' }} />
              )}
              <Typography variant="body2" noWrap sx={{ flex: 1 }}>{option.label}</Typography>
              {option.group && (
                <Typography variant="caption" color="text.secondary">{option.group}</Typography>
              )}
            </>
          )}
        </Box>
      )}
      renderInput={(params) => (
        <TextField {...params} size="small" placeholder={selected ? undefined : placeholder} />
      )}
    />
  );
}
