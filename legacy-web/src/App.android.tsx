import { useTranslation } from 'react-i18next';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Link from '@mui/material/Link';
import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import Snackbar from '@mui/material/Snackbar';
import Container from '@mui/material/Container';
import { useAppShell } from './hooks/useAppShell';
import { createAppMuiTheme } from './theme/muiTheme';
import { useIsDarkMode } from './theme/useDarkMode';
import { TeamsIcon, CustomPokemonIcon, SettingsIcon } from './components/icons/MaterialIcons';
import { TeamsPage } from './components/TeamsPage/TeamsPage';
import { TeamDetailPage } from './components/TeamDetailPage/TeamDetailPage';
import { CustomPkmnPage } from './components/CustomRoster/CustomPkmnPage';
import { SettingsPage } from './components/Settings/SettingsPage';
import { SurpriseMeModal } from './components/SurpriseMe/SurpriseMeModal';
import { LoadingScreen } from './components/LoadingScreen/LoadingScreen';
import './i18n';

export default function App() {
  const {
    data,
    state,
    view,
    setView,
    settings,
    userDataReady,
    includeCustomsAnalysis,
    setIncludeCustomsAnalysis,
    toastMsg,
    installEvent,
    showMoves,
    setShowMoves,
    surpriseMeOpen,
    setSurpriseMeOpen,
    currentTeam,
    updateSettings,
    updateTeam,
    updateMember,
    saveCustom,
    createEmptyTeam,
    handleSurpriseCreate,
    handleImportTeam,
    deleteTeam,
    duplicateTeam,
    applySuggestion,
    handleInstall,
    addCustom,
    renameCustom,
    deleteCustom,
  } = useAppShell();

  const { t } = useTranslation();
  const darkMode = useIsDarkMode(settings.theme);
  const theme = createAppMuiTheme(darkMode);

  const navValue = view.page === 'team' ? 'teams' : view.page;

  if (data.loading || !userDataReady || (data.error && data.pokemon.length === 0)) {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <LoadingScreen
          progress={data.loading ? data.progress : null}
          error={data.pokemon.length === 0 ? data.error : null}
          onRetry={data.refresh}
        />
      </ThemeProvider>
    );
  }

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', pb: 7 }}>
        <AppBar position="sticky" color="default">
          <Toolbar sx={{ gap: 1.5 }}>
            <Chip label="CD" color="primary" size="small" sx={{ fontWeight: 700 }} />
            <Typography variant="h6" component="div" noWrap>
              CoverDex
            </Typography>
            {installEvent && (
              <Button onClick={handleInstall} variant="contained" size="small" sx={{ ml: 'auto' }}>
                Install
              </Button>
            )}
          </Toolbar>
          {view.page === 'team' && currentTeam && (
            <Toolbar variant="dense" sx={{ minHeight: 36 }}>
              <Breadcrumbs>
                <Link
                  component="button"
                  underline="hover"
                  color="inherit"
                  onClick={() => setView({ page: 'teams' })}
                >
                  {t('nav.teams')}
                </Link>
                <Typography color="text.primary">{currentTeam.name}</Typography>
              </Breadcrumbs>
            </Toolbar>
          )}
        </AppBar>

        <Container maxWidth="lg" sx={{ py: 2, flex: 1, display: 'flex', flexDirection: 'column', gap: 3 }}>
          {view.page === 'teams' && (
            <TeamsPage
              teams={state.teams}
              onSelectTeam={(id) => setView({ page: 'team', teamId: id, tab: 'pokemon' })}
              onCreateEmpty={createEmptyTeam}
              onImport={handleImportTeam}
              onRenameTeam={(id, name) => updateTeam(id, (tm) => ({ ...tm, name }))}
              onDuplicateTeam={duplicateTeam}
              onSurpriseMe={() => setSurpriseMeOpen(true)}
            />
          )}

          {view.page === 'team' && currentTeam && (
            <TeamDetailPage
              team={currentTeam}
              tab={view.tab}
              onTabChange={(tab) => setView({ page: 'team', teamId: view.teamId, tab })}
              pokemon={data.pokemon}
              moves={data.moves}
              customs={state.customPokemon}
              typeChart={data.typeChart}
              showMoves={showMoves}
              onShowMovesChange={setShowMoves}
              onUpdateMember={(idx, m) => updateMember(view.teamId, idx, m)}
              onSaveCustom={saveCustom}
              onRenameTeam={(name) => updateTeam(view.teamId, (tm) => ({ ...tm, name }))}
              onDeleteTeam={() => deleteTeam(view.teamId)}
              onApplySuggestion={(s) => applySuggestion(view.teamId, s)}
              includeCustomsAnalysis={includeCustomsAnalysis}
              onIncludeCustomsChange={setIncludeCustomsAnalysis}
              includeMegaDynamax={settings.includeMegaDynamax}
              excludeLegendaries={settings.excludeLegendaries}
            />
          )}

          {view.page === 'custompkmn' && (
            <CustomPkmnPage
              customs={state.customPokemon}
              onAdd={addCustom}
              onRename={renameCustom}
              onDelete={deleteCustom}
            />
          )}

          {view.page === 'settings' && (
            <SettingsPage
              settings={settings}
              onSettingsChange={updateSettings}
              installAvailable={!!installEvent}
              onInstall={handleInstall}
              dataVersion={data.version}
              dataGeneratedAt={data.generatedAt}
              dataPokemonCount={data.pokemon.length}
              dataMoveCount={data.moves.length}
              dataError={data.error}
              dataRefreshing={data.refreshing}
              dataProgress={data.progress}
              onRefreshData={data.refresh}
            />
          )}
        </Container>

        <SurpriseMeModal
          open={surpriseMeOpen}
          onClose={() => setSurpriseMeOpen(false)}
          onCreate={handleSurpriseCreate}
          pokemon={data.pokemon}
          customs={state.customPokemon}
          typeChart={data.typeChart}
        />

        <Snackbar
          open={!!toastMsg}
          message={toastMsg}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        />

        <BottomNavigation
          value={navValue}
          onChange={(_e, value) => setView({ page: value })}
          showLabels
          sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, borderTop: 1, borderColor: 'divider' }}
        >
          <BottomNavigationAction label={t('nav.teams')} value="teams" icon={<TeamsIcon />} />
          <BottomNavigationAction label={t('nav.customPkmn')} value="custompkmn" icon={<CustomPokemonIcon />} />
          <BottomNavigationAction label={t('nav.settings')} value="settings" icon={<SettingsIcon />} />
        </BottomNavigation>
      </Box>
    </ThemeProvider>
  );
}
