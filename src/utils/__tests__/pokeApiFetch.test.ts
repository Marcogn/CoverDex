import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fetchPokemonData, POKEAPI_MIRROR_BASE } from '../pokeApiFetch';

function jsonResponse(body: unknown, ok = true) {
  return {
    ok,
    status: ok ? 200 : 404,
    json: async () => body,
  } as Response;
}

const FIXTURES: Record<string, unknown> = {
  '/pokemon/index.json': {
    count: 2,
    results: [
      { name: 'bulbasaur', url: '/api/v2/pokemon/1/' },
      { name: 'ivysaur', url: '/api/v2/pokemon/2/' },
    ],
  },
  '/pokemon/1/index.json': {
    id: 1,
    name: 'bulbasaur',
    species: { name: 'bulbasaur', url: '/api/v2/pokemon-species/1/' },
    types: [
      { slot: 1, type: { name: 'grass' } },
      { slot: 2, type: { name: 'poison' } },
    ],
    abilities: [{ ability: { name: 'overgrow' }, is_hidden: false, slot: 1 }],
    sprites: {
      front_default: 'pixel1.png',
      other: {
        home: { front_default: 'home1.png' },
        'official-artwork': { front_default: 'art1.png' },
      },
    },
  },
  '/pokemon/2/index.json': {
    id: 2,
    name: 'ivysaur',
    species: { name: 'ivysaur', url: '/api/v2/pokemon-species/2/' },
    types: [{ slot: 1, type: { name: 'grass' } }],
    abilities: [{ ability: { name: 'overgrow' }, is_hidden: false, slot: 1 }],
    sprites: { front_default: 'pixel2.png', other: { home: {}, 'official-artwork': {} } },
  },
  '/pokemon-species/1/index.json': {
    is_legendary: false,
    is_mythical: false,
    evolution_chain: { url: '/api/v2/evolution-chain/1/' },
  },
  '/pokemon-species/2/index.json': {
    is_legendary: false,
    is_mythical: false,
    evolution_chain: { url: '/api/v2/evolution-chain/1/' },
  },
  '/evolution-chain/1/index.json': {
    chain: {
      species: { name: 'bulbasaur' },
      evolves_to: [{ species: { name: 'ivysaur' }, evolves_to: [] }],
    },
  },
  '/type/index.json': {
    count: 2,
    results: [
      { name: 'grass', url: '/api/v2/type/12/' },
      { name: 'poison', url: '/api/v2/type/4/' },
    ],
  },
  '/type/12/index.json': {
    name: 'grass',
    damage_relations: {
      double_damage_to: [{ name: 'water' }],
      half_damage_to: [{ name: 'fire' }],
      no_damage_to: [],
    },
  },
  '/type/4/index.json': {
    name: 'poison',
    damage_relations: {
      double_damage_to: [],
      half_damage_to: [],
      no_damage_to: [{ name: 'steel' }],
    },
  },
  '/move/index.json': {
    count: 1,
    results: [{ name: 'tackle', url: '/api/v2/move/1/' }],
  },
  '/move/1/index.json': {
    id: 1,
    name: 'tackle',
    type: { name: 'normal' },
    power: 40,
    damage_class: { name: 'physical' },
  },
};

function installFetchMock(overrides: Record<string, unknown> = {}, failing: string[] = []) {
  const fetchMock = vi.fn(async (url: string) => {
    const path = url.replace(POKEAPI_MIRROR_BASE, '');
    if (failing.includes(path)) {
      throw new Error('network error');
    }
    const body = path in overrides ? overrides[path] : FIXTURES[path];
    if (body === undefined) throw new Error(`no fixture for ${path}`);
    return jsonResponse(body);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('fetchPokemonData', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('assembles pokemon, moves, and typeChart matching the PokemonDataFile shape', async () => {
    installFetchMock();
    const result = await fetchPokemonData();

    expect(result.pokemon).toHaveLength(2);
    const bulbasaur = result.pokemon.find((p) => p.name === 'bulbasaur')!;
    expect(bulbasaur.displayName).toBe('Bulbasaur');
    expect(bulbasaur.types).toEqual(['grass', 'poison']);
    expect(bulbasaur.defaultAbility).toBe('overgrow');
    expect(bulbasaur.spriteHome).toBe('home1.png');
    expect(bulbasaur.isFinalEvolution).toBe(false);

    const ivysaur = result.pokemon.find((p) => p.name === 'ivysaur')!;
    expect(ivysaur.isFinalEvolution).toBe(true);

    expect(result.moves).toHaveLength(1);
    expect(result.moves[0]).toMatchObject({ name: 'tackle', displayName: 'Tackle', type: 'normal', power: 40, damageClass: 'physical' });

    expect(result.typeChart.grass.water).toBe(2);
    expect(result.typeChart.grass.fire).toBe(0.5);
    expect(result.typeChart.poison.steel).toBe(0);
    expect(result.typeChart.normal.normal).toBe(1);

    expect(result.version).toBe(3);
  });

  it('reports progress per stage', async () => {
    installFetchMock();
    const stages: string[] = [];
    await fetchPokemonData((p) => stages.push(`${p.stage}:${p.done}/${p.total}`));
    expect(stages).toContain('pokemon:2/2');
    expect(stages).toContain('species:2/2');
    expect(stages).toContain('moves:1/1');
  });

  it('retries a failed resource once, then skips it rather than throwing', async () => {
    const fetchMock = installFetchMock({}, []);
    let callCount = 0;
    fetchMock.mockImplementation(async (url: string) => {
      const path = url.replace(POKEAPI_MIRROR_BASE, '');
      if (path === '/pokemon/2/index.json') {
        callCount++;
        if (callCount === 1) throw new Error('transient network error');
      }
      const body = FIXTURES[path];
      if (body === undefined) throw new Error(`no fixture for ${path}`);
      return jsonResponse(body);
    });

    const result = await fetchPokemonData();

    // ivysaur succeeded on retry, so both Pokémon are present
    expect(result.pokemon.map((p) => p.name).sort()).toEqual(['bulbasaur', 'ivysaur']);
    expect(callCount).toBe(2);
  });

  it('throws when every evolution-chain fetch fails (no partial cache)', async () => {
    installFetchMock({}, ['/evolution-chain/1/index.json']);
    await expect(fetchPokemonData()).rejects.toThrow(/No final evolutions found/);
  });
});
