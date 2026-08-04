import { useEffect, useRef, useState } from 'react';
import { Preferences } from '@capacitor/preferences';
import { AppState } from '../types';
import { defaultAppState } from '../utils/teamFactory';
import { UserDataStorage, USER_DATA_KEY } from './useUserDataStorage';

/** Android: teamdex_userdata is persisted through @capacitor/preferences
 * (native SharedPreferences-backed storage) instead of the WebView's
 * localStorage, which isn't guaranteed to survive storage pressure the way
 * native storage is — see ROADMAP.md and CLAUDE.md "Android Platform".
 *
 * Unlike the web version, Preferences reads are async, so the first render
 * can't have real data yet. `state` starts at the same empty-team default
 * used for a genuine first launch, `ready` starts false, and callers
 * (App.android.tsx) hold a loading screen until it flips true — the same
 * pattern already used for the PokéAPI data fetch in usePokemonData.
 *
 * First launch on a device that already had the app installed under the
 * old localStorage-only behavior: if Preferences has nothing yet but the
 * WebView's localStorage does, that data is adopted once, persisted into
 * Preferences, and the old localStorage copy is cleared so it can't get
 * out of sync with the new source of truth.
 */
export function useUserDataStorage(): UserDataStorage {
  const [state, setState] = useState<AppState>(defaultAppState);
  const [ready, setReady] = useState(false);
  const readyRef = useRef(false);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      let loaded: AppState | null = null;

      try {
        const { value } = await Preferences.get({ key: USER_DATA_KEY });
        if (value) loaded = JSON.parse(value) as AppState;
      } catch {
        // corrupt or unreadable Preferences value — fall through to the
        // localStorage migration check, then the default.
      }

      if (!loaded) {
        try {
          const legacyRaw = localStorage.getItem(USER_DATA_KEY);
          if (legacyRaw) {
            loaded = JSON.parse(legacyRaw) as AppState;
            localStorage.removeItem(USER_DATA_KEY);
          }
        } catch {
          // corrupt or unreadable legacy value — keep the default.
        }
      }

      if (cancelled) return;
      if (loaded) setState(loaded);
      readyRef.current = true;
      setReady(true);
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    // Skip until the initial load (and any legacy migration) has resolved —
    // otherwise this fires with the synchronous default and overwrites
    // whatever's already in Preferences before it's even been read.
    if (!readyRef.current) return;
    Preferences.set({ key: USER_DATA_KEY, value: JSON.stringify(state) });
  }, [state]);

  return { state, setState, ready };
}
