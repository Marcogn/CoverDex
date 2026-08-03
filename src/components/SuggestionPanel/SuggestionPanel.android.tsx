import { useMemo, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Avatar from '@mui/material/Avatar';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { Suggestion } from '../../hooks/suggestionEngine';
import { TypeBadge } from '../TypeBadge/TypeBadge';
import { SuggestionFilters, SuggestionFilterState } from './SuggestionFilters';
import { filterByType, pickRandom } from './SuggestionPanel';
import type { SuggestionPanelProps } from './SuggestionPanel';

function SuggestionCard({ suggestion: s, onApply }: { suggestion: Suggestion; onApply?: (s: Suggestion) => void }) {
  const { t } = useTranslation();
  return (
    <Card variant="outlined">
      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Avatar src={s.spriteUrl ?? undefined} sx={{ width: 48, height: 48, bgcolor: 'action.hover' }} />
          <Box>
            <Typography variant="subtitle2">{s.candidateLabel}</Typography>
            <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
              {s.types[0] && <TypeBadge type={s.types[0]} />}
              {s.types[1] && <TypeBadge type={s.types[1]} />}
            </Stack>
          </Box>
        </Stack>
        <Typography variant="caption" color="text.secondary">
          {s.kind === 'add' ? t('suggestions.addToTeam') : t('suggestions.replaces', { name: s.replacesName })}
        </Typography>

        <Stack spacing={0.5}>
          {s.newlyCovered.length > 0 && (
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="caption" color="success.main">✅ {t('suggestions.covers')}</Typography>
              {s.newlyCovered.map((tp) => <TypeBadge key={tp} type={tp} />)}
            </Stack>
          )}
          {s.newWeaknesses.length > 0 && (
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="caption" color="warning.main">⚠️ {t('suggestions.newWeaknesses')}</Typography>
              {s.newWeaknesses.map((tp) => <TypeBadge key={tp} type={tp} />)}
            </Stack>
          )}
          {s.aggravatedWeaknesses.length > 0 && (
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="caption" color="warning.main">⚠️ {t('suggestions.aggravates')}</Typography>
              {s.aggravatedWeaknesses.map((tp) => {
                const memberNames = s.aggravatedMembers.get(tp) ?? [];
                return (
                  <Stack key={tp} direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                    <TypeBadge type={tp} />
                    {memberNames.length > 0 && (
                      <Typography variant="caption" color="text.secondary">
                        ({t('suggestions.alreadyWeak', { names: memberNames.join(', ') })})
                      </Typography>
                    )}
                  </Stack>
                );
              })}
            </Stack>
          )}
          {s.newWeaknesses.length === 0 && s.aggravatedWeaknesses.length === 0 && (
            <Typography variant="caption" color="success.main">✅ {t('suggestions.noNewWeaknesses')}</Typography>
          )}
        </Stack>
      </CardContent>
      {onApply && (
        <CardActions sx={{ justifyContent: 'flex-end' }}>
          <Button size="small" variant="contained" onClick={() => onApply(s)}>
            {s.kind === 'add' ? t('suggestions.addToTeam') : t('suggestions.replaces', { name: s.replacesName })}
          </Button>
        </CardActions>
      )}
    </Card>
  );
}

export function SuggestionPanel({ suggestions, mixedMovesNote, onApply, generation, onGenerationChange }: SuggestionPanelProps) {
  const { t } = useTranslation();
  const [filters, setFilters] = useState<SuggestionFilterState>({
    generation: generation as SuggestionFilterState['generation'],
    types: [],
    mode: 'best',
  });
  const [randomSeed, setRandomSeed] = useState(0);

  const handleFilterChange = useCallback((f: SuggestionFilterState) => {
    setFilters(f);
    if (f.generation !== filters.generation) {
      onGenerationChange(f.generation);
    }
  }, [filters.generation, onGenerationChange]);

  const filtered = useMemo(() => filterByType(suggestions, filters.types), [suggestions, filters.types]);

  const displayed = useMemo(() => {
    if (filters.mode === 'random') {
      return pickRandom(filtered, 10);
    }
    return filtered.slice(0, 10);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtered, filters.mode, randomSeed]);

  const handleRandomize = useCallback(() => setRandomSeed((s) => s + 1), []);

  if (suggestions.length === 0) {
    return <Typography variant="body2" color="text.secondary">{t('suggestions.noSuggestions')}</Typography>;
  }
  const allZero = displayed.every((s) => s.gain === 0);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      {mixedMovesNote && (
        <Alert severity="warning" variant="outlined">
          Some Pokémon have no moves entered — using type-based coverage for those slots.
        </Alert>
      )}

      <SuggestionFilters filters={filters} onChange={handleFilterChange} onRandomize={handleRandomize} />

      {displayed.length === 0 && (
        <Typography variant="body2" color="text.secondary">{t('suggestions.noMatch')}</Typography>
      )}

      {allZero && displayed.length > 0 && (
        <Typography variant="body2" color="text.secondary">{t('suggestions.solidCoverage')}</Typography>
      )}

      <Grid container spacing={1.5}>
        {displayed.map((s, i) => (
          <Grid key={i} size={{ xs: 12, md: 6, xl: 4 }}>
            <SuggestionCard suggestion={s} onApply={onApply} />
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
