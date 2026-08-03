import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const srcDir = path.dirname(fileURLToPath(import.meta.url)).replace(/\/test$/, '');
const thisFile = fileURLToPath(import.meta.url);

// Matches an actual URL construction (`https://pokeapi.co/...`, or a bare
// `//pokeapi.co/...`), not prose that merely mentions the host name in a
// comment (e.g. "unlike the live pokeapi.co REST API").
const POKEAPI_URL_PATTERN = /\/\/[^\s'"`]*pokeapi\.co/;

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
 * CLAUDE.md invariant: the PokéAPI download happens exactly once, from
 * src/utils/pokeApiFetch.ts, and only ever hits the static
 * raw.githubusercontent.com/PokeAPI/api-data mirror — never the live
 * pokeapi.co REST API, which has no CORS/rate-limit guarantees for a
 * client run by every app user. This guards against that regressing,
 * without flagging comments that merely *mention* pokeapi.co (e.g. to
 * explain why the mirror is used instead).
 */
describe('no runtime fetch of the pokeapi.co REST API in src/', () => {
  it('never constructs a pokeapi.co URL outside this guard test itself', () => {
    const offenders = walk(srcDir)
      .filter((file) => file !== thisFile)
      .filter((file) => POKEAPI_URL_PATTERN.test(fs.readFileSync(file, 'utf-8')));

    expect(offenders, `Found pokeapi.co URL references in: ${offenders.join(', ')}`).toEqual([]);
  });
});
