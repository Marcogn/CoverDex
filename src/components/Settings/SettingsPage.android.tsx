import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import FormControl from '@mui/material/FormControl';
import RadioGroup from '@mui/material/RadioGroup';
import Radio from '@mui/material/Radio';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import ToggleButton from '@mui/material/ToggleButton';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import LinearProgress from '@mui/material/LinearProgress';
import Stack from '@mui/material/Stack';
import { AppSettings } from '../../types';
import type { FetchStage } from '../../utils/pokeApiFetch';
import type { SettingsPageProps } from './SettingsPage';

const STAGE_KEY: Record<FetchStage, string> = {
  pokemon: 'loading.stagePokemon',
  species: 'loading.stageSpecies',
  'evolution-chains': 'loading.stageEvolutionChains',
  types: 'loading.stageTypes',
  moves: 'loading.stageMoves',
};

function SectionHeading({ children }: { children: ReactNode }) {
  return (
    <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: 1 }}>
      {children}
    </Typography>
  );
}

export function SettingsPage({
  settings,
  onSettingsChange,
  installAvailable,
  onInstall,
  dataVersion,
  dataGeneratedAt,
  dataPokemonCount,
  dataMoveCount,
  dataError,
  dataRefreshing,
  dataProgress,
  onRefreshData,
}: SettingsPageProps) {
  const { t, i18n } = useTranslation();

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, maxWidth: 640 }}>
      <Typography variant="h5" sx={{ fontWeight: 600 }}>{t('settings.title')}</Typography>

      <Paper variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
        <SectionHeading>{t('settings.teamSuggestions')}</SectionHeading>
        <FormControlLabel
          control={
            <Switch
              checked={settings.includeMegaDynamax}
              onChange={(e) => onSettingsChange({ ...settings, includeMegaDynamax: e.target.checked })}
            />
          }
          label={t('settings.includeMega')}
        />
        <FormControlLabel
          control={
            <Switch
              checked={!settings.excludeLegendaries}
              onChange={(e) => onSettingsChange({ ...settings, excludeLegendaries: !e.target.checked })}
            />
          }
          label={t('settings.includeLegendary')}
        />
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
        <SectionHeading>{t('settings.appearance')}</SectionHeading>
        <FormControl>
          <RadioGroup
            value={settings.theme}
            onChange={(e) => onSettingsChange({ ...settings, theme: e.target.value as AppSettings['theme'] })}
          >
            <FormControlLabel value="system" control={<Radio />} label={t('settings.systemDefault')} />
            <FormControlLabel value="light" control={<Radio />} label={t('settings.light')} />
            <FormControlLabel value="dark" control={<Radio />} label={t('settings.dark')} />
          </RadioGroup>
        </FormControl>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <SectionHeading>{t('settings.language')}</SectionHeading>
        <ToggleButtonGroup
          value={i18n.language}
          exclusive
          onChange={(_e, value) => value && i18n.changeLanguage(value)}
          size="small"
        >
          <ToggleButton value="en">EN</ToggleButton>
          <ToggleButton value="it">IT</ToggleButton>
        </ToggleButtonGroup>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <SectionHeading>{t('settings.data')}</SectionHeading>
        <Chip
          size="small"
          variant="outlined"
          label={
            t('settings.currentData', { version: dataVersion }) +
            (dataGeneratedAt ? `, ${t('settings.generated', { date: new Date(dataGeneratedAt).toLocaleDateString() })}` : '')
          }
          sx={{ alignSelf: 'flex-start' }}
        />
        <Typography variant="caption" color="text.secondary">
          {t('settings.dataCounts', { pokemon: dataPokemonCount, moves: dataMoveCount })}
        </Typography>

        {dataError && <Alert severity="error" variant="outlined">{t('settings.dataError')} {dataError}</Alert>}

        {dataRefreshing && dataProgress && (
          <Stack spacing={0.5}>
            <Typography variant="caption" color="text.secondary">
              {t('settings.refreshing')} {t(STAGE_KEY[dataProgress.stage])} {dataProgress.done}/{dataProgress.total}
            </Typography>
            <LinearProgress
              variant="determinate"
              value={dataProgress.total > 0 ? Math.round((dataProgress.done / dataProgress.total) * 100) : 0}
            />
          </Stack>
        )}

        <Button variant="outlined" size="small" onClick={onRefreshData} disabled={dataRefreshing} sx={{ alignSelf: 'flex-start' }}>
          {dataRefreshing ? t('settings.refreshing') : t('settings.refreshData')}
        </Button>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5, alignItems: 'flex-start' }}>
        <SectionHeading>{t('settings.app')}</SectionHeading>
        <Typography variant="caption" color="text.secondary">
          {t('settings.appVersion', { version: __APP_VERSION__ })}
        </Typography>
        {installAvailable && (
          <Button variant="contained" size="small" onClick={onInstall}>
            {t('settings.installApp')}
          </Button>
        )}
      </Paper>
    </Box>
  );
}
