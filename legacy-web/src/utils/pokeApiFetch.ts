import { MoveEntry, POKEMON_TYPES, PokemonDataFile, PokemonEntry, PokemonType, TypeChart } from '../types';

/**
 * Pure (no React, no localStorage) PokéAPI data-acquisition engine, ported
 * from scripts/generate-pokemon-data.mjs — same shape output, same batching
 * philosophy — but targeting the static PokeAPI/api-data mirror on GitHub
 * instead of the live pokeapi.co REST API, and run client-side instead of
 * at build time. See CLAUDE.md "Key Data Flows" for the invariants this
 * implements (batch size, retry policy, cache versioning).
 *
 * The static mirror only serves numeric-ID paths (e.g. `/pokemon/1/`), not
 * name-based ones (`/pokemon/bulbasaur/` 404s) — verified against the live
 * mirror. Every resource type therefore needs an index lookup first to
 * resolve name -> numeric ID before fetching the detail record.
 */

export const POKEAPI_MIRROR_BASE = 'https://raw.githubusercontent.com/PokeAPI/api-data/master/data/api/v2';

/** localStorage key for the cached PokemonDataFile. Never shares a key with teamdex_userdata. */
export const CACHE_KEY = 'teamdex_pokeapi_cache';

/** Bump whenever the cached payload shape changes — see CLAUDE.md "Cache version" pitfall. */
export const CACHE_VERSION = 3;

const BATCH_SIZE = 50;
const BATCH_DELAY_MS = 50;
const RETRY_DELAY_MS = 500;

export type FetchStage = 'pokemon' | 'species' | 'evolution-chains' | 'types' | 'moves';

export interface FetchProgress {
  stage: FetchStage;
  done: number;
  total: number;
}

interface IndexEntry {
  name: string;
  url: string;
}

interface IndexResponse {
  count: number;
  results: IndexEntry[];
}

function prettify(name: string): string {
  return name
    .split('-')
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(' ');
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

async function fetchJson(url: string, signal?: AbortSignal): Promise<any> {
  const r = await fetch(url, { signal });
  if (!r.ok) throw new Error(`HTTP ${r.status} for ${url}`);
  return r.json();
}

/** One retry per resource, per CLAUDE.md — returns null (skips the resource) if both attempts fail. */
async function fetchWithRetry(url: string, signal?: AbortSignal): Promise<any | null> {
  try {
    return await fetchJson(url, signal);
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err;
    await sleep(RETRY_DELAY_MS);
    try {
      return await fetchJson(url, signal);
    } catch (err2) {
      if (err2 instanceof DOMException && err2.name === 'AbortError') throw err2;
      return null;
    }
  }
}

async function processInBatches<T, R>(
  items: T[],
  worker: (item: T, index: number) => Promise<R>,
  stage: FetchStage,
  onProgress?: (p: FetchProgress) => void,
): Promise<R[]> {
  const results: R[] = [];
  for (let i = 0; i < items.length; i += BATCH_SIZE) {
    const slice = items.slice(i, i + BATCH_SIZE);
    const batchResults = await Promise.all(slice.map((item, j) => worker(item, i + j)));
    results.push(...batchResults);
    const done = Math.min(i + BATCH_SIZE, items.length);
    onProgress?.({ stage, done, total: items.length });
    if (i + BATCH_SIZE < items.length) await sleep(BATCH_DELAY_MS);
  }
  return results;
}

function extractIdFromUrl(url: string | undefined | null): string | null {
  if (!url) return null;
  const last = url.split('/').filter(Boolean).pop();
  return last && /^\d+$/.test(last) ? last : null;
}

function collectFinalEvolutions(node: any, out: Set<string>): void {
  if (!node.evolves_to || node.evolves_to.length === 0) {
    out.add(node.species.name);
    return;
  }
  for (const child of node.evolves_to) collectFinalEvolutions(child, out);
}

/**
 * Fetches and assembles the full PokemonDataFile from the static mirror.
 * Throws if the fetch can't produce usable data (e.g. every evolution-chain
 * request failed) — callers should surface that as a cache-write failure,
 * not write a partial cache (CLAUDE.md: "complete and versioned, or fully
 * absent — never partial").
 */
export async function fetchPokemonData(
  onProgress?: (p: FetchProgress) => void,
  signal?: AbortSignal,
): Promise<PokemonDataFile> {
  // 1. Pokémon list
  const listData: IndexResponse = await fetchJson(`${POKEAPI_MIRROR_BASE}/pokemon/index.json`, signal);

  // 2. Pokémon details
  const pokemonRaw: any[] = [];
  const pokemonResults = await processInBatches(
    listData.results,
    async (entry) => {
      const id = extractIdFromUrl(entry.url);
      if (!id) return null;
      return fetchWithRetry(`${POKEAPI_MIRROR_BASE}/pokemon/${id}/index.json`, signal);
    },
    'pokemon',
    onProgress,
  );
  for (const p of pokemonResults) if (p) pokemonRaw.push(p);

  // 3. Species (legendary/mythical flags + evolution chain pointer)
  const speciesIdByName = new Map<string, string>();
  for (const p of pokemonRaw) {
    if (!speciesIdByName.has(p.species.name)) {
      const id = extractIdFromUrl(p.species.url);
      if (id) speciesIdByName.set(p.species.name, id);
    }
  }
  const speciesEntries = Array.from(speciesIdByName.entries());
  const speciesFlags = new Map<string, { legendary: boolean; mythical: boolean }>();
  const evoChainUrls = new Set<string>();

  await processInBatches(
    speciesEntries,
    async ([name, id]) => {
      const data = await fetchWithRetry(`${POKEAPI_MIRROR_BASE}/pokemon-species/${id}/index.json`, signal);
      if (data) {
        speciesFlags.set(name, { legendary: !!data.is_legendary, mythical: !!data.is_mythical });
        if (data.evolution_chain?.url) evoChainUrls.add(data.evolution_chain.url);
      }
    },
    'species',
    onProgress,
  );

  // 4. Evolution chains -> final-evolution species names
  const finalEvolutions = new Set<string>();
  await processInBatches(
    Array.from(evoChainUrls),
    async (url) => {
      const id = extractIdFromUrl(url);
      if (!id) return;
      const data = await fetchWithRetry(`${POKEAPI_MIRROR_BASE}/evolution-chain/${id}/index.json`, signal);
      if (data?.chain) collectFinalEvolutions(data.chain, finalEvolutions);
    },
    'evolution-chains',
    onProgress,
  );

  if (finalEvolutions.size === 0) {
    throw new Error(
      'No final evolutions found — evolution chain fetches likely all failed. Cannot build a valid cache.',
    );
  }

  // 5. Type chart. The mirror's type/index.json list gives us the name -> numeric ID
  // mapping (no name-based path works), then each of the 18 tracked types' detail
  // record has the damage_relations table.
  const typeChart: TypeChart = {} as TypeChart;
  for (const a of POKEMON_TYPES) {
    typeChart[a] = {} as Record<PokemonType, number>;
    for (const b of POKEMON_TYPES) typeChart[a][b] = 1;
  }

  const typeIndex: IndexResponse = await fetchJson(`${POKEAPI_MIRROR_BASE}/type/index.json`, signal);
  const typeIdByName = new Map<string, string>();
  for (const t of typeIndex.results) {
    const id = extractIdFromUrl(t.url);
    if (id) typeIdByName.set(t.name, id);
  }
  const relevantTypes = POKEMON_TYPES.filter((t) => typeIdByName.has(t));

  const typeResults = await processInBatches(
    relevantTypes,
    async (typeName) => {
      const id = typeIdByName.get(typeName)!;
      const data = await fetchWithRetry(`${POKEAPI_MIRROR_BASE}/type/${id}/index.json`, signal);
      return { typeName, data };
    },
    'types',
    onProgress,
  );

  for (const { typeName, data } of typeResults) {
    if (!data?.damage_relations) continue;
    for (const x of data.damage_relations.double_damage_to) {
      if (typeChart[typeName]) typeChart[typeName][x.name as PokemonType] = 2;
    }
    for (const x of data.damage_relations.half_damage_to) {
      if (typeChart[typeName]) typeChart[typeName][x.name as PokemonType] = 0.5;
    }
    for (const x of data.damage_relations.no_damage_to) {
      if (typeChart[typeName]) typeChart[typeName][x.name as PokemonType] = 0;
    }
  }

  // 6. Moves
  const moveIndex: IndexResponse = await fetchJson(`${POKEAPI_MIRROR_BASE}/move/index.json`, signal);
  const movesRaw: MoveEntry[] = [];
  const moveResults = await processInBatches(
    moveIndex.results,
    async (entry) => {
      const id = extractIdFromUrl(entry.url);
      if (!id) return null;
      return fetchWithRetry(`${POKEAPI_MIRROR_BASE}/move/${id}/index.json`, signal);
    },
    'moves',
    onProgress,
  );
  for (const m of moveResults) {
    if (m) {
      movesRaw.push({
        id: m.id,
        name: m.name,
        displayName: prettify(m.name),
        type: m.type.name,
        power: m.power,
        damageClass: m.damage_class.name,
      });
    }
  }
  const moves = movesRaw.sort((a, b) => a.id - b.id);

  // --- Assemble Pokémon entries ---
  const pokemon: PokemonEntry[] = pokemonRaw
    .map((p): PokemonEntry => {
      const types = p.types
        .slice()
        .sort((a: any, b: any) => a.slot - b.slot)
        .map((t: any) => t.type.name);
      const flags = speciesFlags.get(p.species.name);
      const defaultAbility = p.abilities
        ?.slice()
        .sort((a: any, b: any) => a.slot - b.slot)
        .find((a: any) => !a.is_hidden)?.ability?.name;

      return {
        id: p.id,
        name: p.name,
        displayName: prettify(p.name),
        speciesName: p.species.name,
        types: [types[0] ?? 'normal', types[1] ?? null],
        spriteHome: p.sprites?.other?.home?.front_default ?? null,
        spriteArtwork: p.sprites?.other?.['official-artwork']?.front_default ?? null,
        spriteDefault: p.sprites?.front_default ?? null,
        isLegendary: flags?.legendary ?? false,
        isMythical: flags?.mythical ?? false,
        isFinalEvolution: finalEvolutions.has(p.species.name),
        defaultAbility,
      };
    })
    .sort((a, b) => a.id - b.id);

  return {
    generatedAt: new Date().toISOString(),
    version: CACHE_VERSION,
    pokemon,
    moves,
    typeChart,
  };
}
