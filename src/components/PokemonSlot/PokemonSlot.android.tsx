import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { v4 as uuid } from 'uuid';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Avatar from '@mui/material/Avatar';
import Typography from '@mui/material/Typography';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import Grid from '@mui/material/Grid';
import {
  MoveEntry,
  POKEMON_TYPES,
  PokemonEntry,
  PokemonMove,
  PokemonType,
  TeamMember,
} from '../../types';
import { SearchableDropdown, DropdownOption } from '../SearchableDropdown/SearchableDropdown';
import { TypeBadge } from '../TypeBadge/TypeBadge';
import { MoveSlot } from '../MoveSlot/MoveSlot';
import { resolveSpriteUrl } from '../../utils/spriteUtils';
import { getAbilityEffects, normalizeAbilityName } from '../../data/abilityEffects';
import { AbilityDropdown } from '../AbilityDropdown/AbilityDropdown';
import type { PokemonSlotProps } from './PokemonSlot';

export function PokemonSlot({
  member,
  pokemon,
  moves,
  customs,
  showMoves,
  onChange,
  onSaveCustom,
  onClear,
}: PokemonSlotProps) {
  const [includeCustoms, setIncludeCustoms] = useState(false);
  const { t } = useTranslation();

  const options = useMemo<DropdownOption<PokemonEntry | TeamMember>[]>(() => {
    const customOpts: DropdownOption<TeamMember>[] = includeCustoms
      ? customs.map((c) => ({
          key: 'c-' + c.id,
          label: c.speciesName,
          value: c,
          spriteUrl: null,
          group: 'CUSTOM',
        }))
      : [];
    const apiOpts: DropdownOption<PokemonEntry>[] = pokemon.map((p) => ({
      key: 'p-' + p.id + '-' + p.name,
      label: p.displayName,
      value: p,
      spriteUrl: resolveSpriteUrl(p, 'dropdown'),
    }));
    return [...customOpts, ...apiOpts] as DropdownOption<PokemonEntry | TeamMember>[];
  }, [pokemon, customs, includeCustoms]);

  function selectPokemon(_: unknown, opt: DropdownOption<PokemonEntry | TeamMember> | null) {
    if (!opt) {
      onChange(null);
      return;
    }
    const v = opt.value;
    if ((v as TeamMember).moves) {
      const c = v as TeamMember;
      onChange({ ...c, id: uuid(), isCustomSaved: true });
    } else {
      const p = v as PokemonEntry;
      onChange({
        id: uuid(),
        speciesName: p.displayName,
        spriteUrl: resolveSpriteUrl(p, 'card'),
        types: p.types,
        moves: [null, null, null, null],
        isCustomSaved: false,
        ability: p.defaultAbility,
      });
    }
  }

  function setType(idx: 0 | 1, t: PokemonType | null) {
    if (!member) return;
    const next: [PokemonType, PokemonType | null] = [...member.types] as [PokemonType, PokemonType | null];
    if (idx === 0 && t) next[0] = t;
    if (idx === 1) next[1] = t;
    onChange({ ...member, types: next });
  }

  function setMove(i: number, mv: PokemonMove | null) {
    if (!member) return;
    const ms = [...member.moves] as TeamMember['moves'];
    ms[i] = mv;
    onChange({ ...member, moves: ms });
  }

  function setAbility(ability: string) {
    if (!member) return;
    onChange({ ...member, ability: ability || undefined });
  }

  const effects = member ? getAbilityEffects(member.ability) : null;
  const hasImmunity = effects?.some((e) => e.kind === 'immunity') ?? false;
  const hasMultiplier = effects?.some((e) => e.kind === 'multiplier') ?? false;
  const isFluffy = normalizeAbilityName(member?.ability ?? '') === 'fluffy';
  const isWonderGuard = normalizeAbilityName(member?.ability ?? '') === 'wonder-guard';
  const effectTooltip = effects
    ?.filter((e) => e.kind !== 'badge-only')
    .map((e) => (e.kind === 'immunity' ? `Immune to ${e.type}` : `×${e.factor} ${e.type}`))
    .join(', ');

  return (
    <Card variant="outlined">
      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <SearchableDropdown
          options={options}
          value={null}
          placeholder={member ? member.speciesName : 'Choose Pokémon…'}
          onChange={selectPokemon}
        />
        <FormControlLabel
          control={<Switch size="small" checked={includeCustoms} onChange={(e) => setIncludeCustoms(e.target.checked)} />}
          label={<Typography variant="caption">Include saved custom Pokémon in search</Typography>}
        />

        {member ? (
          <>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <Avatar src={member.spriteUrl ?? undefined} sx={{ width: 56, height: 56, bgcolor: 'action.hover' }} />
              <Box>
                <Typography variant="subtitle2">{member.speciesName}</Typography>
                <Stack direction="row" spacing={0.5}>
                  {member.types[0] && <TypeBadge type={member.types[0]} />}
                  {member.types[1] && <TypeBadge type={member.types[1]} />}
                </Stack>
              </Box>
            </Stack>

            <Grid container spacing={1}>
              <Grid size={6}>
                <FormControl size="small" fullWidth>
                  <InputLabel>Type 1</InputLabel>
                  <Select label="Type 1" value={member.types[0]} onChange={(e) => setType(0, e.target.value as PokemonType)}>
                    {POKEMON_TYPES.map((ty) => (
                      <MenuItem key={ty} value={ty}>{ty}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={6}>
                <FormControl size="small" fullWidth>
                  <InputLabel>Type 2</InputLabel>
                  <Select
                    label="Type 2"
                    value={member.types[1] ?? ''}
                    onChange={(e) => setType(1, e.target.value ? (e.target.value as PokemonType) : null)}
                  >
                    <MenuItem value="">None</MenuItem>
                    {POKEMON_TYPES.map((ty) => (
                      <MenuItem key={ty} value={ty}>{ty}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </Grid>

            <Box>
              <Typography variant="caption" color="text.secondary">{t('slot.ability')}</Typography>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Box sx={{ flex: 1 }}>
                  <AbilityDropdown value={member.ability ?? ''} onChange={setAbility} />
                </Box>
                {(hasImmunity || hasMultiplier) && (
                  <Tooltip title={effectTooltip ?? ''}>
                    <Chip label="⚡" size="small" color="success" />
                  </Tooltip>
                )}
                {isWonderGuard && <Chip label="Wonder Guard" size="small" color="secondary" />}
                {isFluffy && (
                  <Tooltip title={t('slot.fluffyNote')}>
                    <Chip label="⚠️" size="small" color="warning" />
                  </Tooltip>
                )}
              </Stack>
            </Box>

            {showMoves && (
              <Grid container spacing={1}>
                {[0, 1, 2, 3].map((i) => (
                  <Grid key={i} size={{ xs: 12, sm: 6 }}>
                    <MoveSlot move={member.moves[i]} moves={moves} onChange={(mv) => setMove(i, mv)} />
                  </Grid>
                ))}
              </Grid>
            )}

            <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
              <Button size="small" variant="contained" onClick={() => onSaveCustom(member)}>
                Save as custom
              </Button>
              <Button size="small" onClick={onClear}>Clear slot</Button>
            </Stack>
          </>
        ) : (
          <Typography variant="body2" color="text.secondary">Empty slot</Typography>
        )}
      </CardContent>
    </Card>
  );
}
