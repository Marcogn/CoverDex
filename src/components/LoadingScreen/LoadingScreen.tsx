import { useTranslation } from 'react-i18next';
import type { FetchProgress, FetchStage } from '../../utils/pokeApiFetch';

export interface LoadingScreenProps {
  progress: FetchProgress | null;
  error: string | null;
  onRetry: () => void;
}

const STAGE_KEY: Record<FetchStage, string> = {
  pokemon: 'loading.stagePokemon',
  species: 'loading.stageSpecies',
  'evolution-chains': 'loading.stageEvolutionChains',
  types: 'loading.stageTypes',
  moves: 'loading.stageMoves',
};

/**
 * Blocking first-launch screen shown only while there is no usable cached
 * data yet (see usePokemonData's `loading` flag) — never shown for a
 * background/manual refresh, which keeps the existing data usable instead.
 */
export function LoadingScreen({ progress, error, onRetry }: LoadingScreenProps) {
  const { t } = useTranslation();

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white dark:bg-bg text-gray-900 dark:text-slate-100 px-4">
        <div className="max-w-sm text-center flex flex-col gap-3">
          <h1 className="text-lg font-semibold text-red-600 dark:text-red-300">{t('loading.errorTitle')}</h1>
          <p className="text-sm text-gray-500 dark:text-slate-400">{error}</p>
          <button
            onClick={onRetry}
            className="mt-2 px-4 py-2 rounded bg-accent hover:bg-violet-500 font-semibold text-white self-center"
          >
            {t('loading.retry')}
          </button>
        </div>
      </div>
    );
  }

  const pct = progress && progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 0;

  return (
    <div className="min-h-screen flex items-center justify-center bg-white dark:bg-bg text-gray-900 dark:text-slate-100 px-4">
      <div className="max-w-sm w-full flex flex-col gap-4 items-center text-center">
        <span className="bg-accent text-white px-2 py-0.5 rounded text-sm font-bold">CD</span>
        <h1 className="text-lg font-semibold">{t('loading.title')}</h1>
        <p className="text-sm text-gray-500 dark:text-slate-400">{t('loading.subtitle')}</p>
        <div className="w-full h-2 bg-gray-100 dark:bg-panel2 rounded overflow-hidden">
          <div
            className="h-full bg-accent transition-[width] duration-200"
            style={{ width: `${pct}%` }}
          />
        </div>
        {progress && (
          <span className="text-xs text-gray-500 dark:text-slate-500">
            {t(STAGE_KEY[progress.stage])} {progress.done}/{progress.total}
          </span>
        )}
      </div>
    </div>
  );
}
