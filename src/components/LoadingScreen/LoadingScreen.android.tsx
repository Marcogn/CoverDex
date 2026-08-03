import { useTranslation } from 'react-i18next';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import LinearProgress from '@mui/material/LinearProgress';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import type { FetchStage } from '../../utils/pokeApiFetch';
import type { LoadingScreenProps } from './LoadingScreen';

const STAGE_KEY: Record<FetchStage, string> = {
  pokemon: 'loading.stagePokemon',
  species: 'loading.stageSpecies',
  'evolution-chains': 'loading.stageEvolutionChains',
  types: 'loading.stageTypes',
  moves: 'loading.stageMoves',
};

export function LoadingScreen({ progress, error, onRetry }: LoadingScreenProps) {
  const { t } = useTranslation();

  if (error) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', px: 3 }}>
        <Stack spacing={2} sx={{ alignItems: 'center', maxWidth: 360, textAlign: 'center' }}>
          <Typography variant="h6" color="error">{t('loading.errorTitle')}</Typography>
          <Typography variant="body2" color="text.secondary">{error}</Typography>
          <Button variant="contained" onClick={onRetry}>{t('loading.retry')}</Button>
        </Stack>
      </Box>
    );
  }

  const pct = progress && progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 0;

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', px: 3 }}>
      <Stack spacing={2} sx={{ alignItems: 'center', maxWidth: 360, width: '100%', textAlign: 'center' }}>
        <Chip label="CD" color="primary" sx={{ fontWeight: 700 }} />
        <Typography variant="h6">{t('loading.title')}</Typography>
        <Typography variant="body2" color="text.secondary">{t('loading.subtitle')}</Typography>
        <Box sx={{ width: '100%' }}>
          <LinearProgress variant="determinate" value={pct} />
        </Box>
        {progress && (
          <Typography variant="caption" color="text.secondary">
            {t(STAGE_KEY[progress.stage])} {progress.done}/{progress.total}
          </Typography>
        )}
      </Stack>
    </Box>
  );
}
