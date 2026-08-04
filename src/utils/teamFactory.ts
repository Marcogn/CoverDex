import { v4 as uuid } from 'uuid';
import { AppState, Team } from '../types';

/** Pure team/state constructors shared by useAppShell and the per-platform
 * user-data storage hooks (useUserDataStorage[.android].ts) — both need a
 * fresh starter team, one to hand out on "New team", the other as the
 * fallback default before any persisted data has been read. */
export function emptyTeam(name = 'New Team'): Team {
  return {
    id: uuid(),
    name,
    members: [null, null, null, null, null, null],
    createdAt: Date.now(),
  };
}

export function defaultAppState(): AppState {
  const t = emptyTeam('My First Team');
  return { teams: [t], customPokemon: [], activeTeamId: t.id };
}
