import { useCallback, useEffect, useRef, useState } from 'react';
import { MoveEntry, PokemonDataFile, PokemonEntry, TypeChart } from '../types';
import { CACHE_KEY, CACHE_VERSION, FetchProgress, fetchPokemonData } from '../utils/pokeApiFetch';

export interface PokeDataState {
  pokemon: PokemonEntry[];
  moves: MoveEntry[];
  typeChart: TypeChart | null;
  /** True only for the initial download with no usable cache yet — callers should block the UI on this. */
  loading: boolean;
  /** True while a manual/background refresh runs after usable data already exists — callers should show a non-blocking indicator. */
  refreshing: boolean;
  error: string | null;
  version: number;
  generatedAt: string | null;
  progress: FetchProgress | null;
  /** Clears the cache and re-downloads. Existing data (if any) stays usable until the refresh completes. */
  refresh: () => void;
}

interface CacheEnvelope {
  version: number;
  data: PokemonDataFile;
}

function readCache(): PokemonDataFile | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CacheEnvelope;
    if (parsed.version !== CACHE_VERSION) return null;
    if (!parsed.data || !Array.isArray(parsed.data.pokemon) || parsed.data.pokemon.length === 0) return null;
    return parsed.data;
  } catch {
    return null;
  }
}

function writeCache(data: PokemonDataFile): void {
  try {
    const envelope: CacheEnvelope = { version: CACHE_VERSION, data };
    localStorage.setItem(CACHE_KEY, JSON.stringify(envelope));
  } catch {
    // Storage full/unavailable — in-memory state still works for this session.
  }
}

/**
 * Downloads the Pokémon/move/type-chart dataset once, on first launch, from
 * the static PokeAPI mirror (see src/utils/pokeApiFetch.ts), caches it in
 * localStorage under teamdex_pokeapi_cache, and never re-fetches
 * automatically after that — only via the exposed `refresh()` (wired to the
 * Settings "Data" section's manual refresh button). Never touches
 * teamdex_userdata. See CLAUDE.md "Key Data Flows".
 */
export function usePokemonData(): PokeDataState {
  const [payload, setPayload] = useState<PokemonDataFile | null>(() => readCache());
  const [fetching, setFetching] = useState(() => payload === null);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<FetchProgress | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const generationRef = useRef(0);

  const runFetch = useCallback(() => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const generation = ++generationRef.current;

    setFetching(true);
    setError(null);
    setProgress(null);

    fetchPokemonData((p) => {
      if (generationRef.current === generation) setProgress(p);
    }, controller.signal)
      .then((data) => {
        if (generationRef.current !== generation) return;
        writeCache(data);
        setPayload(data);
        setFetching(false);
        setProgress(null);
      })
      .catch((err) => {
        if (controller.signal.aborted || generationRef.current !== generation) return;
        setError(err instanceof Error ? err.message : 'Failed to download Pokémon data.');
        setFetching(false);
        setProgress(null);
      });
  }, []);

  useEffect(() => {
    if (payload === null) runFetch();
    return () => {
      abortRef.current?.abort();
    };
    // Intentionally run once on mount only — refresh() is the only other entry point.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refresh = useCallback(() => {
    try {
      localStorage.removeItem(CACHE_KEY);
    } catch {
      // ignore
    }
    runFetch();
  }, [runFetch]);

  return {
    pokemon: payload?.pokemon ?? [],
    moves: payload?.moves ?? [],
    typeChart: payload?.typeChart ?? null,
    loading: fetching && payload === null,
    refreshing: fetching && payload !== null,
    error,
    version: payload?.version ?? 0,
    generatedAt: payload?.generatedAt ?? null,
    progress,
    refresh,
  };
}
