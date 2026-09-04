import { useCallback, useEffect, useState } from 'react';
import { v4 as uuid } from 'uuid';
import { AppSettings, AppView, PokemonEntry, Team, TeamMember } from '../types';
import { usePokemonData } from './usePokemonData';
import { useUserDataStorage } from './useUserDataStorage';
import { Suggestion } from './suggestionEngine';
import { parseShowdownTeam } from '../utils/showdownParser';
import { resolveSpriteUrl } from '../utils/spriteUtils';
import { emptyTeam } from '../utils/teamFactory';

export const DEFAULT_SETTINGS: AppSettings = {
  theme: 'system',
  includeMegaDynamax: false,
  excludeLegendaries: false,
};

export interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export function applyTheme(theme: AppSettings['theme']) {
  const html = document.documentElement;
  if (theme === 'dark') {
    html.classList.add('dark');
  } else if (theme === 'light') {
    html.classList.remove('dark');
  } else {
    // system
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
      html.classList.add('dark');
    } else {
      html.classList.remove('dark');
    }
  }
}

/**
 * All CoverDex app-level state and handlers, shared verbatim between the web
 * (Tailwind) and Android (MUI) presentations. Only JSX/markup diverges per
 * platform — see CLAUDE.md "Android platform" for the file convention.
 */
export function useAppShell() {
  const data = usePokemonData();
  const { state, setState, ready: userDataReady } = useUserDataStorage();
  const [view, setView] = useState<AppView>({ page: 'teams' });
  const [includeCustomsAnalysis, setIncludeCustomsAnalysis] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const [installEvent, setInstallEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [showMoves, setShowMoves] = useState(false);
  const [surpriseMeOpen, setSurpriseMeOpen] = useState(false);

  const settings: AppSettings = state.settings ?? DEFAULT_SETTINGS;

  // Apply theme on boot and when settings change
  useEffect(() => {
    applyTheme(settings.theme);
  }, [settings.theme]);

  // Listen for system theme changes when in 'system' mode
  useEffect(() => {
    if (settings.theme !== 'system') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => applyTheme('system');
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, [settings.theme]);

  useEffect(() => {
    function handler(e: Event) {
      e.preventDefault();
      setInstallEvent(e as BeforeInstallPromptEvent);
    }
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  const toast = useCallback((msg: string) => {
    setToastMsg(msg);
    window.setTimeout(() => setToastMsg(null), 2500);
  }, []);

  const updateSettings = useCallback((s: AppSettings) => {
    setState((prev) => ({ ...prev, settings: s }));
  }, []);

  const getTeam = useCallback(
    (id: string) => state.teams.find((t) => t.id === id),
    [state.teams],
  );

  const updateTeam = useCallback(
    (teamId: string, mut: (t: Team) => Team) => {
      setState((s) => ({
        ...s,
        teams: s.teams.map((t) => (t.id === teamId ? mut(t) : t)),
      }));
    },
    [],
  );

  const updateMember = useCallback(
    (teamId: string, idx: number, m: TeamMember | null) => {
      updateTeam(teamId, (t) => {
        const members = [...t.members];
        members[idx] = m;
        return { ...t, members };
      });
    },
    [updateTeam],
  );

  const saveCustom = useCallback(
    (m: TeamMember) => {
      setState((s) => ({
        ...s,
        customPokemon: [...s.customPokemon, { ...m, id: uuid(), isCustomSaved: true }],
      }));
      toast('Custom Pokémon saved');
    },
    [toast],
  );

  const createEmptyTeam = useCallback(() => {
    const t = emptyTeam(`Team ${state.teams.length + 1}`);
    setState((s) => ({ ...s, teams: [...s.teams, t], activeTeamId: t.id }));
    setView({ page: 'team', teamId: t.id, tab: 'pokemon' });
  }, [state.teams.length]);

  const handleSurpriseCreate = useCallback((members: TeamMember[]) => {
    const teamMembers: (TeamMember | null)[] = members.slice(0, 6);
    while (teamMembers.length < 6) teamMembers.push(null);
    const t: Team = {
      id: uuid(),
      name: `Team ${state.teams.length + 1}`,
      members: teamMembers,
      createdAt: Date.now(),
    };
    setState((s) => ({ ...s, teams: [...s.teams, t], activeTeamId: t.id }));
    setView({ page: 'team', teamId: t.id, tab: 'pokemon' });
    toast('Team created');
  }, [state.teams.length, toast]);

  const handleImportTeam = useCallback((text: string) => {
    const resolveMove = (name: string) => {
      const found = data.moves.find((m) => m.displayName.toLowerCase() === name.toLowerCase());
      if (!found) return null;
      return {
        id: 'imp-' + found.id,
        name: found.displayName,
        type: found.type,
        power: found.power,
        damageClass: found.damageClass,
        isCustom: false,
      };
    };
    const resolveTypes = (name: string) => {
      const found = data.pokemon.find((p) => p.displayName.toLowerCase() === name.toLowerCase());
      if (!found) return null;
      return found.types;
    };
    const parsed = parseShowdownTeam(text, resolveMove, resolveTypes);
    const skipped: string[] = [];
    const accepted = parsed.filter((p) => {
      if (!p.speciesKnown) {
        skipped.push(p.speciesName);
        return false;
      }
      return true;
    });
    const members: (TeamMember | null)[] = accepted.slice(0, 6).map((p) => {
      const found = data.pokemon.find((pp) => pp.displayName.toLowerCase() === p.member.speciesName.toLowerCase());
      return { ...p.member, spriteUrl: resolveSpriteUrl(found, 'card') };
    });
    while (members.length < 6) members.push(null);

    const unknown = Array.from(new Set(accepted.flatMap((p) => p.unknownMoveNames)));
    const t: Team = {
      id: uuid(),
      name: `Imported Team ${state.teams.length + 1}`,
      members,
      createdAt: Date.now(),
    };
    setState((s) => ({ ...s, teams: [...s.teams, t], activeTeamId: t.id }));
    setView({ page: 'team', teamId: t.id, tab: 'pokemon' });

    for (const name of skipped) {
      toast(`Could not import ${name}: Pokémon not found in database. Skipping.`);
    }
    if (unknown.length > 0) {
      toast(`Imported team. ${unknown.length} move(s) need type/power: ${unknown.join(', ')}`);
    } else if (skipped.length === 0) {
      toast('Imported team');
    }
  }, [data.moves, data.pokemon, state.teams.length, toast]);

  const deleteTeam = useCallback((id: string) => {
    setState((s) => {
      const teams = s.teams.filter((t) => t.id !== id);
      const next = teams.length === 0 ? [emptyTeam()] : teams;
      return { ...s, teams: next, activeTeamId: next[0].id };
    });
    setView({ page: 'teams' });
  }, []);

  const duplicateTeam = useCallback((id: string) => {
    setState((s) => {
      const source = s.teams.find((t) => t.id === id);
      if (!source) return s;
      const copy: Team = {
        id: uuid(),
        name: `${source.name} (copy)`,
        createdAt: Date.now(),
        members: source.members.map((m) =>
          m === null
            ? null
            : {
                ...m,
                id: uuid(),
                moves: m.moves.map((mv) =>
                  mv === null ? null : { ...mv, id: uuid() },
                ) as typeof m.moves,
              },
        ),
      };
      return { ...s, teams: [...s.teams, copy], activeTeamId: copy.id };
    });
    toast('Team duplicated');
  }, [toast]);

  const applySuggestion = useCallback(
    (teamId: string, s: Suggestion) => {
      const entry = 'id' in s.candidate && typeof (s.candidate as PokemonEntry).name === 'string'
        ? s.candidate as PokemonEntry
        : null;
      const newMember: TeamMember = {
        id: uuid(),
        speciesName: s.candidateLabel,
        spriteUrl: entry ? resolveSpriteUrl(entry, 'card') : s.spriteUrl,
        types: s.types,
        moves: [null, null, null, null],
        isCustomSaved: false,
      };

      if (s.kind === 'add') {
        updateTeam(teamId, (t) => {
          const membersCopy = [...t.members];
          const emptyIdx = membersCopy.findIndex((m) => m === null);
          const slotIdx = emptyIdx >= 0 ? emptyIdx : membersCopy.length;
          if (slotIdx < 6) {
            membersCopy[slotIdx] = newMember;
          }
          toast(`${s.candidateLabel} added to slot ${slotIdx + 1}`);
          return { ...t, members: membersCopy };
        });
      } else {
        updateTeam(teamId, (t) => {
          const membersCopy = [...t.members];
          const replaceIdx = membersCopy.findIndex(
            (m) => m !== null && m.id === s.replacesMemberId,
          );
          if (replaceIdx >= 0) {
            membersCopy[replaceIdx] = newMember;
            toast(`${s.candidateLabel} replaced ${s.replacesName} in slot ${replaceIdx + 1}`);
          }
          return { ...t, members: membersCopy };
        });
      }
      setView({ page: 'team', teamId, tab: 'pokemon' });
    },
    [updateTeam, toast],
  );

  const handleInstall = useCallback(async () => {
    if (!installEvent) return;
    await installEvent.prompt();
    await installEvent.userChoice;
    setInstallEvent(null);
  }, [installEvent]);

  const addCustom = useCallback((m: TeamMember) => {
    setState((s) => ({ ...s, customPokemon: [...s.customPokemon, m] }));
    toast('Custom Pokémon saved');
  }, [toast]);

  const renameCustom = useCallback((id: string, name: string) => {
    setState((s) => ({
      ...s,
      customPokemon: s.customPokemon.map((c) => (c.id === id ? { ...c, speciesName: name } : c)),
    }));
  }, []);

  const deleteCustom = useCallback((id: string) => {
    setState((s) => ({ ...s, customPokemon: s.customPokemon.filter((c) => c.id !== id) }));
    toast('Custom Pokémon deleted');
  }, [toast]);

  // Current team for detail page
  const currentTeam = view.page === 'team' ? getTeam(view.teamId) : undefined;

  return {
    data,
    state,
    view,
    setView,
    settings,
    userDataReady,
    includeCustomsAnalysis,
    setIncludeCustomsAnalysis,
    toastMsg,
    installEvent,
    showMoves,
    setShowMoves,
    surpriseMeOpen,
    setSurpriseMeOpen,
    currentTeam,
    toast,
    updateSettings,
    updateTeam,
    updateMember,
    saveCustom,
    createEmptyTeam,
    handleSurpriseCreate,
    handleImportTeam,
    deleteTeam,
    duplicateTeam,
    applySuggestion,
    handleInstall,
    addCustom,
    renameCustom,
    deleteCustom,
  };
}
