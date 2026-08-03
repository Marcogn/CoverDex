import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const srcDir = path.dirname(fileURLToPath(import.meta.url)).replace(/\/test$/, '');
const thisFile = fileURLToPath(import.meta.url);

function walk(dir: string): string[] {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return walk(full);
    if (/\.(ts|tsx)$/.test(entry.name)) return [full];
    return [];
  });
}

/**
 * CLAUDE.md invariant: the PokéAPI download is build-time only
 * (scripts/generate-pokemon-data.mjs), never a runtime fetch from the app.
 * usePokemonData.ts statically imports the pre-generated pokemon-data.json.
 * Sprite <img> URLs point at raw.githubusercontent.com/PokeAPI, which is a
 * static asset CDN, not the pokeapi.co REST API — those are fine and not
 * what this guards against.
 */
describe('no runtime PokéAPI fetch in src/', () => {
  it('never references the pokeapi.co REST API host outside build-time scripts', () => {
    const offenders = walk(srcDir)
      .filter((file) => file !== thisFile)
      .filter((file) => fs.readFileSync(file, 'utf-8').includes('pokeapi.co'));

    expect(offenders, `Found pokeapi.co references in: ${offenders.join(', ')}`).toEqual([]);
  });
});
