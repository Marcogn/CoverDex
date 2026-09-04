import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Box from '@mui/material/Box';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Stack from '@mui/material/Stack';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import { MoveEntry, PokemonEntry, Team, TeamMember, TypeChart } from '../../types';
import { TeamBuilder } from '../TeamBuilder/TeamBuilder';
import { CoverageGrid } from '../CoverageGrid/CoverageGrid';
import { SuggestionPanel } from '../SuggestionPanel/SuggestionPanel';
import { ExportModal } from '../Modal/ExportModal';
import { DeleteTeamModal } from '../Modal/DeleteTeamModal';
import { useCoverageAnalysis } from '../../hooks/useCoverageAnalysis';
import { useSuggestions } from '../../hooks/useSuggestions';
import type { TeamDetailPageProps } from './TeamDetailPage';

export function TeamDetailPage({
  team,
  tab,
  onTabChange,
  pokemon,
  moves,
  customs,
  typeChart,
  showMoves,
  onShowMovesChange,
  onUpdateMember,
  onSaveCustom,
  onRenameTeam,
  onDeleteTeam,
  onApplySuggestion,
  includeCustomsAnalysis,
  onIncludeCustomsChange,
  includeMegaDynamax,
  excludeLegendaries,
}: TeamDetailPageProps) {
  const { t } = useTranslation();
  const [exportOpen, setExportOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [generation, setGeneration] = useState<string>('all');

  const members = team.members.filter((m): m is TeamMember => m !== null);

  const analysisMembers = useMemo<TeamMember[]>(
    () =>
      showMoves
        ? members
        : members.map((m) => ({ ...m, moves: [null, null, null, null] })),
    [members, showMoves],
  );

  const filteredPool = includeMegaDynamax
    ? pokemon
    : pokemon.filter((p) => !/-mega|-gmax|-dynamax|-mega-x|-mega-y/.test(p.name));

  const analysis = useCoverageAnalysis(typeChart, analysisMembers);
  const suggestions = useSuggestions(
    typeChart,
    analysisMembers,
    filteredPool,
    customs,
    { includeCustoms: includeCustomsAnalysis, excludeLegendaries, generation },
  );

  const canAnalyse = members.length > 0;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Stack direction="row" sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        <Tabs value={tab} onChange={(_e, v) => onTabChange(v)}>
          <Tab value="pokemon" label={t('teamDetail.pokemonTab')} />
          <Tab value="analysis" label={t('teamDetail.analysisTab')} />
        </Tabs>
        <Stack direction="row" spacing={1} sx={{ ml: 'auto' }}>
          {tab === 'pokemon' && (
            <Button size="small" onClick={() => setExportOpen(true)}>{t('teamDetail.export')}</Button>
          )}
          <Button size="small" color="error" onClick={() => setDeleteOpen(true)}>
            {t('teamDetail.deleteTeam')}
          </Button>
        </Stack>
      </Stack>

      {tab === 'pokemon' && (
        <>
          <TeamBuilder
            team={team}
            pokemon={pokemon}
            moves={moves}
            customs={customs}
            showMoves={showMoves}
            onShowMovesChange={onShowMovesChange}
            onUpdateMember={onUpdateMember}
            onSaveCustom={onSaveCustom}
            onRenameTeam={onRenameTeam}
          />
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              disabled={!canAnalyse}
              onClick={() => onTabChange('analysis')}
              variant="contained"
              size="large"
            >
              {t('teamDetail.analyzeTeam')}
            </Button>
            <FormControlLabel
              control={
                <Switch
                  size="small"
                  checked={includeCustomsAnalysis}
                  onChange={(e) => onIncludeCustomsChange(e.target.checked)}
                />
              }
              label={<Typography variant="caption">{t('teamDetail.includeCustom')}</Typography>}
            />
          </Stack>
        </>
      )}

      {tab === 'analysis' && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          {!canAnalyse && (
            <Typography variant="body2" color="text.secondary">{t('analysis.empty')}</Typography>
          )}
          {canAnalyse && typeChart && analysis && (
            <>
              <CoverageGrid chart={typeChart} members={analysisMembers} />
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{t('analysis.uncoveredTypes')}</Typography>
                {analysis.team.uncovered.length === 0 ? (
                  <Alert severity="success">{t('analysis.fullCoverage')}</Alert>
                ) : (
                  <Alert severity="warning">
                    {t('analysis.missingCoverage')}{' '}
                    <strong>{analysis.team.uncovered.join(', ')}</strong>
                  </Alert>
                )}
              </Box>
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{t('analysis.suggestions')}</Typography>
                <SuggestionPanel
                  suggestions={suggestions}
                  mixedMovesNote={analysis.team.mixed}
                  onApply={onApplySuggestion}
                  generation={generation}
                  onGenerationChange={setGeneration}
                />
              </Box>
            </>
          )}
        </Box>
      )}

      <ExportModal open={exportOpen} onClose={() => setExportOpen(false)} team={team} />
      <DeleteTeamModal
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={onDeleteTeam}
        teamName={team.name}
      />
    </Box>
  );
}
