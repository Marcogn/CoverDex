import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useUserDataStorage, USER_DATA_KEY } from '../useUserDataStorage';

beforeEach(() => {
  localStorage.clear();
});

describe('useUserDataStorage (web)', () => {
  it('is ready immediately and starts with a fresh default team when localStorage is empty', () => {
    const { result } = renderHook(() => useUserDataStorage());
    expect(result.current.ready).toBe(true);
    expect(result.current.state.teams).toHaveLength(1);
    expect(result.current.state.teams[0].name).toBe('My First Team');
  });

  it('loads persisted state from localStorage synchronously on first render', () => {
    localStorage.setItem(
      USER_DATA_KEY,
      JSON.stringify({
        teams: [{ id: 't1', name: 'Saved Team', members: Array(6).fill(null), createdAt: 1 }],
        customPokemon: [],
        activeTeamId: 't1',
      }),
    );

    const { result } = renderHook(() => useUserDataStorage());
    expect(result.current.state.teams[0].name).toBe('Saved Team');
  });

  it('writes state changes back to localStorage', () => {
    const { result } = renderHook(() => useUserDataStorage());

    act(() => {
      result.current.setState((s) => ({ ...s, teams: [...s.teams, { ...s.teams[0], id: 't2', name: 'Second' }] }));
    });

    const saved = JSON.parse(localStorage.getItem(USER_DATA_KEY)!);
    expect(saved.teams.map((t: { name: string }) => t.name)).toEqual(['My First Team', 'Second']);
  });

  it('falls back to a fresh default when the stored value is corrupt JSON', () => {
    localStorage.setItem(USER_DATA_KEY, '{not json');
    const { result } = renderHook(() => useUserDataStorage());
    expect(result.current.state.teams).toHaveLength(1);
    expect(result.current.state.teams[0].name).toBe('My First Team');
  });
});
