import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { usePokemonData } from '../usePokemonData';
import { CACHE_KEY, CACHE_VERSION, POKEAPI_MIRROR_BASE } from '../../utils/pokeApiFetch';
import type { PokemonDataFile } from '../../types';

const SAMPLE_DATA: PokemonDataFile = {
  generatedAt: '2026-01-01T00:00:00Z',
  version: CACHE_VERSION,
  pokemon: [
    {
      id: 143,
      name: 'snorlax',
      displayName: 'Snorlax',
      speciesName: 'snorlax',
      types: ['normal', null],
      spriteHome: 'home.png',
      spriteArtwork: 'art.png',
      spriteDefault: 'pixel.png',
      isLegendary: false,
      isMythical: false,
      isFinalEvolution: true,
    },
  ],
  moves: [
    { id: 1, name: 'tackle', displayName: 'Tackle', type: 'normal', power: 40, damageClass: 'physical' },
  ],
  typeChart: { normal: { normal: 1 } } as PokemonDataFile['typeChart'],
};

function jsonResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

// Minimal but valid fixtures so a real fetchPokemonData() run (triggered
// when no cache is present) resolves successfully instead of erroring out
// (fetchPokemonData refuses to produce a dataset with zero final
// evolutions, so this needs at least one complete Pokémon/species/chain).
const MINIMAL_FIXTURES: Record<string, unknown> = {
  '/pokemon/index.json': { count: 1, results: [{ name: 'testmon', url: '/api/v2/pokemon/1/' }] },
  '/pokemon/1/index.json': {
    id: 1,
    name: 'testmon',
    species: { name: 'testmon', url: '/api/v2/pokemon-species/1/' },
    types: [{ slot: 1, type: { name: 'normal' } }],
    abilities: [],
    sprites: { front_default: null, other: { home: {}, 'official-artwork': {} } },
  },
  '/pokemon-species/1/index.json': {
    is_legendary: false,
    is_mythical: false,
    evolution_chain: { url: '/api/v2/evolution-chain/1/' },
  },
  '/evolution-chain/1/index.json': {
    chain: { species: { name: 'testmon' }, evolves_to: [] },
  },
  '/type/index.json': { count: 0, results: [] },
  '/move/index.json': { count: 0, results: [] },
};

function installEmptyFetchMock() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const path = url.replace(POKEAPI_MIRROR_BASE, '');
      const body = MINIMAL_FIXTURES[path];
      if (body === undefined) throw new Error(`no fixture for ${path}`);
      return jsonResponse(body);
    }),
  );
}

beforeEach(() => {
  localStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('usePokemonData — cached data present', () => {
  beforeEach(() => {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ version: CACHE_VERSION, data: SAMPLE_DATA }));
  });

  it('loads immediately from localStorage without fetching', () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);

    const { result } = renderHook(() => usePokemonData());
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.pokemon).toHaveLength(1);
    expect(result.current.pokemon[0].name).toBe('snorlax');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('exposes moves, typeChart, version and generatedAt from the cache', () => {
    vi.stubGlobal('fetch', vi.fn());
    const { result } = renderHook(() => usePokemonData());
    expect(result.current.moves).toHaveLength(1);
    expect(result.current.moves[0].name).toBe('tackle');
    expect(result.current.typeChart).not.toBeNull();
    expect(result.current.version).toBe(CACHE_VERSION);
    expect(result.current.generatedAt).toBe('2026-01-01T00:00:00Z');
  });

  it('refresh() clears the cache and re-fetches, keeping old data visible as "refreshing" until it completes', async () => {
    installEmptyFetchMock();
    const { result } = renderHook(() => usePokemonData());
    expect(result.current.pokemon).toHaveLength(1);

    act(() => result.current.refresh());
    expect(result.current.refreshing).toBe(true);
    expect(result.current.pokemon).toHaveLength(1); // old data still there during refresh

    await waitFor(() => expect(result.current.refreshing).toBe(false));
    expect(result.current.pokemon.map((p) => p.name)).toEqual(['testmon']); // replaced by the re-fetch
    expect(localStorage.getItem(CACHE_KEY)).not.toBeNull();
  });
});

describe('usePokemonData — no cache (first launch)', () => {
  it('fetches from the network and reports loading until it completes', async () => {
    installEmptyFetchMock();
    const { result } = renderHook(() => usePokemonData());
    expect(result.current.loading).toBe(true);
    expect(result.current.refreshing).toBe(false);

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeNull();
    expect(result.current.pokemon.map((p) => p.name)).toEqual(['testmon']);
    expect(localStorage.getItem(CACHE_KEY)).not.toBeNull();
  });

  it('surfaces an error and clears loading when the fetch fails, without touching teamdex_userdata', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new Error('network down');
      }),
    );
    localStorage.setItem('teamdex_userdata', '"untouched"');

    const { result } = renderHook(() => usePokemonData());
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeTruthy();
    expect(result.current.pokemon).toHaveLength(0);
    expect(localStorage.getItem('teamdex_userdata')).toBe('"untouched"');
  });

  it('ignores a stale-versioned cache and re-fetches', async () => {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ version: CACHE_VERSION - 1, data: SAMPLE_DATA }));
    installEmptyFetchMock();

    const { result } = renderHook(() => usePokemonData());
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.pokemon.map((p) => p.name)).toEqual(['testmon']); // re-fetched, not the stale cached snorlax
  });
});
