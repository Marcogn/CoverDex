import { useTranslation } from 'react-i18next';
import { useAppShell } from './hooks/useAppShell';
import { TeamsPage } from './components/TeamsPage/TeamsPage';
import { TeamDetailPage } from './components/TeamDetailPage/TeamDetailPage';
import { CustomPkmnPage } from './components/CustomRoster/CustomPkmnPage';
import { SettingsPage } from './components/Settings/SettingsPage';
import { SurpriseMeModal } from './components/SurpriseMe/SurpriseMeModal';
import './i18n';

export default function App() {
  const {
    data,
    state,
    view,
    setView,
    settings,
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

  return (
    <div className="min-h-screen bg-white dark:bg-bg text-gray-900 dark:text-slate-100">
      {/* Header */}
      <header className="flex items-center gap-3 px-4 py-2 bg-white dark:bg-[#1a1a2e] border-b border-gray-200 dark:border-white/10 sticky top-0 z-50">
        <span className="bg-accent text-white px-2 py-0.5 rounded text-sm font-bold">CD</span>
        <span className="text-base font-semibold whitespace-nowrap text-gray-900 dark:text-white">CoverDex</span>
        <span
          className={`text-[10px] px-1.5 py-0.5 rounded ${
            data.version === 0 ? 'bg-red-700 text-red-100' : 'bg-panel2 text-slate-400'
          }`}
          title={data.generatedAt ? `Generated: ${new Date(data.generatedAt).toLocaleString()}` : undefined}
        >
          {data.version === 0 ? 'data: not generated' : `data v${data.version}`}
        </span>
        {installEvent && (
          <button
            onClick={handleInstall}
            className="ml-auto text-xs px-2 py-1 rounded bg-accent hover:bg-violet-500"
          >
            Install
          </button>
        )}
      </header>

      {/* Nav */}
      <nav className="flex gap-1 px-4 py-1 bg-white dark:bg-[#1a1a2e] border-b border-gray-200 dark:border-white/10 sticky top-[40px] z-50">
        <button
          className={`px-3 py-1.5 rounded text-sm whitespace-nowrap ${
            view.page === 'teams' || view.page === 'team' ? 'bg-accent text-white' : 'hover:bg-gray-100 dark:hover:bg-panel2 text-gray-700 dark:text-slate-100'
          }`}
          onClick={() => setView({ page: 'teams' })}
        >
          {t('nav.teams')}
        </button>
        <button
          className={`px-3 py-1.5 rounded text-sm whitespace-nowrap ${
            view.page === 'custompkmn' ? 'bg-accent text-white' : 'hover:bg-gray-100 dark:hover:bg-panel2 text-gray-700 dark:text-slate-100'
          }`}
          onClick={() => setView({ page: 'custompkmn' })}
        >
          {t('nav.customPkmn')}
        </button>
        <button
          className={`px-3 py-1.5 rounded text-sm whitespace-nowrap ${
            view.page === 'settings' ? 'bg-accent text-white' : 'hover:bg-gray-100 dark:hover:bg-panel2 text-gray-700 dark:text-slate-100'
          }`}
          onClick={() => setView({ page: 'settings' })}
        >
          {t('nav.settings')}
        </button>
      </nav>

      {/* Breadcrumb */}
      {view.page === 'team' && currentTeam && (
        <div className="max-w-6xl mx-auto px-4 pt-2 text-sm text-slate-400">
          <button className="hover:text-slate-200 underline" onClick={() => setView({ page: 'teams' })}>
            Teams
          </button>
          <span className="mx-1">&gt;</span>
          <span className="text-slate-200">{currentTeam.name}</span>
        </div>
      )}

      {data.error && (
        <div className="max-w-6xl mx-auto px-4 mt-4 bg-red-900/40 text-red-200 p-3 rounded text-sm">
          {data.error}
        </div>
      )}

      <main className="max-w-6xl mx-auto px-4 py-4 flex flex-col gap-6">
        {view.page === 'teams' && (
          <TeamsPage
            teams={state.teams}
            onSelectTeam={(id) => setView({ page: 'team', teamId: id, tab: 'pokemon' })}
            onCreateEmpty={createEmptyTeam}
            onImport={handleImportTeam}
            onRenameTeam={(id, name) => updateTeam(id, (t) => ({ ...t, name }))}
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
            onRenameTeam={(name) => updateTeam(view.teamId, (t) => ({ ...t, name }))}
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
          />
        )}
      </main>

      <SurpriseMeModal
        open={surpriseMeOpen}
        onClose={() => setSurpriseMeOpen(false)}
        onCreate={handleSurpriseCreate}
        pokemon={data.pokemon}
        customs={state.customPokemon}
        typeChart={data.typeChart}
      />

      {toastMsg && (
        <div className="fixed bottom-4 right-4 bg-white dark:bg-panel border border-gray-200 dark:border-panel2 px-3 py-2 rounded shadow-xl text-sm text-gray-900 dark:text-slate-100">
          {toastMsg}
        </div>
      )}
    </div>
  );
}
