import { Dispatch, SetStateAction, useEffect, useState } from 'react';
import { AppState } from '../types';
import { defaultAppState } from '../utils/teamFactory';

export const USER_DATA_KEY = 'teamdex_userdata';

export interface UserDataStorage {
  state: AppState;
  setState: Dispatch<SetStateAction<AppState>>;
  /** Always true on the web build — localStorage reads are synchronous, so
   * there is nothing to wait for. Exists so useAppShell has one consistent
   * shape across platforms; see useUserDataStorage.android.ts for the
   * platform where this actually gates anything. */
  ready: boolean;
}

function loadAppState(): AppState {
  try {
    const raw = localStorage.getItem(USER_DATA_KEY);
    if (raw) return JSON.parse(raw) as AppState;
  } catch {
    // ignore, fall through to a fresh default
  }
  return defaultAppState();
}

/** Web/PWA: teamdex_userdata lives in the browser's localStorage, read
 * synchronously on first render and written back on every change. Unchanged
 * behavior from before this hook was extracted out of useAppShell.ts. */
export function useUserDataStorage(): UserDataStorage {
  const [state, setState] = useState<AppState>(loadAppState);

  useEffect(() => {
    localStorage.setItem(USER_DATA_KEY, JSON.stringify(state));
  }, [state]);

  return { state, setState, ready: true };
}
