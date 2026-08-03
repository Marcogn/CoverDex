import type { ReactNode } from 'react';
import { useTheme } from '@mui/material/styles';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Chip from '@mui/material/Chip';
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import { useTranslation } from 'react-i18next';
import { POKEMON_TYPES, PokemonType, TeamMember, TypeChart } from '../../types';
import { defensiveMultiplier, memberHasMoves } from '../../utils/coverageEngine';
import { TypeBadge } from '../TypeBadge/TypeBadge';
import { getAbilityEffects, normalizeAbilityName } from '../../data/abilityEffects';
import { collectAttackingTypes, multLabel } from './CoverageGrid';
import type { CoverageGridProps } from './CoverageGrid';

/** Palette-aware version of the web build's Tailwind cellClass() mapping. */
function useCellColors() {
  const theme = useTheme();
  return function cellSx(mult: number) {
    if (mult === 0) return { bgcolor: theme.palette.error.dark, color: theme.palette.error.contrastText };
    if (mult >= 2) return { bgcolor: theme.palette.success.dark, color: theme.palette.success.contrastText };
    if (mult > 0 && mult < 1) return { bgcolor: theme.palette.warning.dark, color: theme.palette.warning.contrastText };
    return { bgcolor: 'action.hover' };
  };
}

const NAME_COL_WIDTH = 120;

function StickyHeadCell({ children, corner }: { children: ReactNode; corner?: boolean }) {
  return (
    <TableCell
      sx={{
        position: 'sticky',
        top: 0,
        left: corner ? 0 : undefined,
        zIndex: corner ? 4 : 2,
        bgcolor: 'background.paper',
        textAlign: corner ? 'left' : 'center',
        width: corner ? NAME_COL_WIDTH : 40,
        minWidth: corner ? NAME_COL_WIDTH : 40,
      }}
    >
      {children}
    </TableCell>
  );
}

function NameCell({ member, bold }: { member: TeamMember | { speciesName: string; spriteUrl?: null }; bold?: boolean }) {
  const sprite = 'spriteUrl' in member ? member.spriteUrl : null;
  return (
    <TableCell
      sx={{
        position: 'sticky',
        left: 0,
        zIndex: 2,
        bgcolor: 'background.paper',
        width: NAME_COL_WIDTH,
        maxWidth: NAME_COL_WIDTH,
        fontWeight: bold ? 700 : 400,
      }}
    >
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
        {sprite && <Avatar src={sprite} variant="square" sx={{ width: 20, height: 20, bgcolor: 'transparent' }} />}
        <Typography variant="caption" noWrap title={member.speciesName}>{member.speciesName}</Typography>
      </Stack>
    </TableCell>
  );
}

function DefRow({ label, types }: { label: string; types: PokemonType[] }) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
      <Typography variant="caption" color="text.secondary" sx={{ width: 130, flexShrink: 0 }}>{label}</Typography>
      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>
        {types.map((tp) => <TypeBadge key={tp} type={tp} />)}
      </Stack>
    </Stack>
  );
}

function PerPokemonAccordion({ member, chart }: { member: TeamMember; chart: TypeChart }) {
  const { t } = useTranslation();

  const weak4x: PokemonType[] = [];
  const weak2x: PokemonType[] = [];
  const resist05x: PokemonType[] = [];
  const resist025x: PokemonType[] = [];
  const immune: PokemonType[] = [];

  for (const atk of POKEMON_TYPES) {
    const mult = defensiveMultiplier(chart, atk, member.types, member.ability);
    if (mult === 0) immune.push(atk);
    else if (mult >= 4) weak4x.push(atk);
    else if (mult >= 2) weak2x.push(atk);
    else if (mult <= 0.25) resist025x.push(atk);
    else if (mult < 1) resist05x.push(atk);
  }

  const hasMoves = memberHasMoves(member);
  const moveTypes: PokemonType[] = hasMoves
    ? member.moves
        .filter((mv) => mv && mv.damageClass !== 'status' && (mv.power ?? 0) > 0)
        .map((mv) => (mv as { type: PokemonType }).type)
    : [];

  const effects = member.ability ? getAbilityEffects(member.ability) : null;
  const isWonderGuard = normalizeAbilityName(member.ability ?? '') === 'wonder-guard';
  const effectSummary = effects
    ?.filter((e) => e.kind !== 'badge-only')
    .map((e) => (e.kind === 'immunity' ? `${t('analysis.immuneTo')} ${e.type}` : `×${e.factor} ${e.type}`))
    .join(', ');

  return (
    <Accordion variant="outlined" disableGutters>
      <AccordionSummary>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          {member.spriteUrl && <Avatar src={member.spriteUrl} sx={{ width: 32, height: 32, bgcolor: 'action.hover' }} />}
          <Typography variant="subtitle2">{member.speciesName}</Typography>
          <Stack direction="row" spacing={0.5}>
            {member.types[0] && <TypeBadge type={member.types[0]} />}
            {member.types[1] && <TypeBadge type={member.types[1]} />}
          </Stack>
        </Stack>
      </AccordionSummary>
      <AccordionDetails sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {member.ability && (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
            <Typography variant="caption" color="text.secondary" sx={{ width: 130, flexShrink: 0 }}>{t('slot.ability')}</Typography>
            <Typography variant="caption">
              {member.ability}
              {effectSummary && <Box component="span" sx={{ color: 'success.main', ml: 0.5 }}>— {effectSummary}</Box>}
              {isWonderGuard && <Box component="span" sx={{ color: 'secondary.main', ml: 0.5 }}>— {t('analysis.wonderGuardNote')}</Box>}
            </Typography>
          </Stack>
        )}
        {weak4x.length > 0 && <DefRow label={t('defensive.weaknesses4x')} types={weak4x} />}
        {weak2x.length > 0 && <DefRow label={t('defensive.weaknesses2x')} types={weak2x} />}
        {resist05x.length > 0 && <DefRow label={t('defensive.resistances05x')} types={resist05x} />}
        {resist025x.length > 0 && <DefRow label={t('defensive.resistances025x')} types={resist025x} />}
        {immune.length > 0 && <DefRow label={t('defensive.immune0x')} types={immune} />}
        {hasMoves && moveTypes.length > 0 && <DefRow label={t('defensive.moveCoverage')} types={moveTypes} />}
      </AccordionDetails>
    </Accordion>
  );
}

export function CoverageGrid({ chart, members }: CoverageGridProps) {
  const { t } = useTranslation();
  const cellSx = useCellColors();

  const allHaveMoves = members.length > 0 && members.every(memberHasMoves);
  const noneHaveMoves = members.every((m) => !memberHasMoves(m));
  const mixed = !allHaveMoves && !noneHaveMoves;

  let basisNotice = t('analysis.basisTypesOnly');
  if (allHaveMoves) {
    basisNotice = t('analysis.basisMovesOnly');
  } else if (mixed) {
    const moveNames = members.filter(memberHasMoves).map((m) => m.speciesName).join(', ');
    const typeNames = members.filter((m) => !memberHasMoves(m)).map((m) => m.speciesName).join(', ');
    basisNotice = t('analysis.basisMixed', { moveNames, typeNames });
  }

  const weaknessCounts = new Map<PokemonType, number>();
  for (const atk of POKEMON_TYPES) {
    let count = 0;
    for (const m of members) {
      if (defensiveMultiplier(chart, atk, m.types, m.ability) > 1) count++;
    }
    if (count >= 2) weaknessCounts.set(atk, count);
  }
  const sortedWeaknesses = [...weaknessCounts.entries()].sort((a, b) => b[1] - a[1]);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Alert severity="info">{basisNotice}</Alert>

      <Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{t('analysis.perPokemon')}</Typography>
        <Stack spacing={1}>
          {members.map((m) => (
            <PerPokemonAccordion key={m.id} member={m} chart={chart} />
          ))}
        </Stack>
      </Box>

      <Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{t('analysis.offensiveCoverage')}</Typography>
        <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 480, overflowX: 'auto' }}>
          <Table size="small" stickyHeader sx={{ tableLayout: 'fixed' }}>
            <TableHead>
              <TableRow>
                <StickyHeadCell corner>Pokémon</StickyHeadCell>
                {POKEMON_TYPES.map((tp) => (
                  <StickyHeadCell key={tp}><TypeBadge type={tp} /></StickyHeadCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {members.map((m) => {
                const attackingTypes = collectAttackingTypes(m);
                return (
                  <TableRow key={m.id}>
                    <NameCell member={m} />
                    {POKEMON_TYPES.map((def) => {
                      let best = 0;
                      for (const atk of attackingTypes) {
                        const v = chart[atk]?.[def] ?? 1;
                        if (v > best) best = v;
                      }
                      return (
                        <TableCell key={def} align="center" sx={cellSx(best)}>{multLabel(best)}</TableCell>
                      );
                    })}
                  </TableRow>
                );
              })}
              <TableRow>
                <NameCell member={{ speciesName: t('analysis.teamBest') }} bold />
                {POKEMON_TYPES.map((def) => {
                  let best = 0;
                  for (const m of members) {
                    for (const atk of collectAttackingTypes(m)) {
                      const v = chart[atk]?.[def] ?? 1;
                      if (v > best) best = v;
                    }
                  }
                  return (
                    <TableCell key={def} align="center" sx={{ fontWeight: 700, ...cellSx(best) }}>{multLabel(best)}</TableCell>
                  );
                })}
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>
      </Box>

      <Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{t('analysis.defensiveCoverage')}</Typography>
        <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 480, overflowX: 'auto' }}>
          <Table size="small" stickyHeader sx={{ tableLayout: 'fixed' }}>
            <TableHead>
              <TableRow>
                <StickyHeadCell corner>Pokémon</StickyHeadCell>
                {POKEMON_TYPES.map((tp) => (
                  <StickyHeadCell key={tp}><TypeBadge type={tp} /></StickyHeadCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {members.map((m) => (
                <TableRow key={m.id}>
                  <NameCell member={m} />
                  {POKEMON_TYPES.map((atk) => {
                    const mult = defensiveMultiplier(chart, atk, m.types, m.ability);
                    return (
                      <TableCell key={atk} align="center" sx={cellSx(mult)}>{multLabel(mult)}</TableCell>
                    );
                  })}
                </TableRow>
              ))}
              <TableRow>
                <NameCell member={{ speciesName: t('analysis.mostVulnerable') }} bold />
                {POKEMON_TYPES.map((atk) => {
                  let worst = 0;
                  for (const m of members) {
                    const mult = defensiveMultiplier(chart, atk, m.types, m.ability);
                    if (mult > worst) worst = mult;
                  }
                  return (
                    <TableCell key={atk} align="center" sx={{ fontWeight: 700, ...cellSx(worst) }}>{multLabel(worst)}</TableCell>
                  );
                })}
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>
      </Box>

      <Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{t('analysis.sharedWeaknesses')}</Typography>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
          {sortedWeaknesses.map(([tp, count]) => (
            <Chip key={tp} icon={<TypeBadge type={tp} />} label={`×${count}`} size="small" variant="outlined" />
          ))}
          {sortedWeaknesses.length === 0 && (
            <Typography variant="caption" color="text.secondary">{t('analysis.noSharedWeaknesses')}</Typography>
          )}
        </Stack>
      </Box>
    </Box>
  );
}
