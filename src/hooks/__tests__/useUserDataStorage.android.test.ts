import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { Preferences } from '@capacitor/preferences';
import { useUserDataStorage } from '../useUserDataStorage.android';
import { USER_DATA_KEY } from '../useUserDataStorage';

const store = new Map<string, string>();

vi.mock('@capacitor/preferences', () => ({
  Preferences: {
    get: vi.fn(async ({ key }: { key: string }) => ({ value: store.get(key) ?? null })),
    set: vi.fn(async ({ key, value }: { key: string; value: string }) => {
      store.set(key, value);
    }),
  },
}));

beforeEach(() => {
  store.clear();
  localStorage.clear();
  vi.clearAllMocks();
});

describe('useUserDataStorage (android)', () => {
  it('starts with the default team and not ready before Preferences resolves', async () => {
    const { result } = renderHook(() => useUserDataStorage());
    expect(result.current.ready).toBe(false);
    expect(result.current.state.teams[0].name).toBe('My First Team');
    // Let the initial async load settle before the test ends, so its state
    // update doesn't land outside of act() after this test has finished.
    await waitFor(() => expect(result.current.ready).toBe(true));
  });

  it('loads persisted state from Preferences and flips ready once resolved', async () => {
    store.set(
      USER_DATA_KEY,
      JSON.stringify({
        teams: [{ id: 't1', name: 'From Preferences', members: Array(6).fill(null), createdAt: 1 }],
        customPokemon: [],
        activeTeamId: 't1',
      }),
    );

    const { result } = renderHook(() => useUserDataStorage());
    await waitFor(() => expect(result.current.ready).toBe(true));
    expect(result.current.state.teams[0].name).toBe('From Preferences');
  });

  it('does not write to Preferences before the initial load has resolved', async () => {
    const { result } = renderHook(() => useUserDataStorage());
    // Synchronous default render — the write-effect must not have fired yet.
    expect(Preferences.set).not.toHaveBeenCalled();

    await waitFor(() => expect(result.current.ready).toBe(true));
  });

  it('migrates legacy localStorage data into Preferences on first load, then clears it', async () => {
    localStorage.setItem(
      USER_DATA_KEY,
      JSON.stringify({
        teams: [{ id: 'legacy', name: 'Legacy Team', members: Array(6).fill(null), createdAt: 1 }],
        customPokemon: [],
        activeTeamId: 'legacy',
      }),
    );

    const { result } = renderHook(() => useUserDataStorage());
    await waitFor(() => expect(result.current.ready).toBe(true));

    expect(result.current.state.teams[0].name).toBe('Legacy Team');
    expect(localStorage.getItem(USER_DATA_KEY)).toBeNull();
    await waitFor(() => expect(store.get(USER_DATA_KEY)).toContain('Legacy Team'));
  });

  it('falls back to the default when both Preferences and legacy localStorage are empty', async () => {
    const { result } = renderHook(() => useUserDataStorage());
    await waitFor(() => expect(result.current.ready).toBe(true));
    expect(result.current.state.teams[0].name).toBe('My First Team');
  });

  it('falls back to the default when the Preferences value is corrupt JSON', async () => {
    store.set(USER_DATA_KEY, '{not json');
    const { result } = renderHook(() => useUserDataStorage());
    await waitFor(() => expect(result.current.ready).toBe(true));
    expect(result.current.state.teams[0].name).toBe('My First Team');
  });

  it('writes state changes to Preferences once ready', async () => {
    const { result } = renderHook(() => useUserDataStorage());
    await waitFor(() => expect(result.current.ready).toBe(true));

    act(() => {
      result.current.setState((s) => ({ ...s, teams: [...s.teams, { ...s.teams[0], id: 't2', name: 'Second' }] }));
    });

    await waitFor(() => {
      const saved = JSON.parse(store.get(USER_DATA_KEY) ?? '{}');
      expect(saved.teams?.map((t: { name: string }) => t.name)).toEqual(['My First Team', 'Second']);
    });
  });
});
