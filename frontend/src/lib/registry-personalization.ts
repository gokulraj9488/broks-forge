"use client";

import { useCallback, useSyncExternalStore } from "react";

/**
 * Personal, client-side organization for the Registry — favorites, pins, collections, saved views, colored
 * labels and recently-viewed. This is deliberately local (localStorage) so it needs NO backend and NO new
 * persistence: it's a per-user overlay on top of the read-only registry, exactly the kind of quality-of-life
 * layer an engineer expects, without touching platform data or the P9 API contract.
 */

export interface Collection {
  id: string;
  name: string;
  color: string;
  items: string[];
}

export interface Label {
  id: string;
  name: string;
  color: string;
}

export interface SavedView {
  id: string;
  name: string;
  query: Record<string, unknown>;
}

export interface PersonalizationState {
  favorites: string[];
  pins: string[];
  recent: string[];
  collections: Collection[];
  labels: Label[];
  itemLabels: Record<string, string[]>;
  savedViews: SavedView[];
}

export const LABEL_COLORS = [
  "#fb7185", "#fbbf24", "#34d399", "#38bdf8", "#a78bfa", "#f472b6", "#2dd4bf", "#e879f9",
];

const KEY = "bf.registry.v1";
const RECENT_MAX = 12;

const EMPTY: PersonalizationState = {
  favorites: [],
  pins: [],
  recent: [],
  collections: [],
  labels: [],
  itemLabels: {},
  savedViews: [],
};

let state: PersonalizationState = EMPTY;
let loaded = false;
const listeners = new Set<() => void>();

function uid(): string {
  try {
    if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  } catch {
    /* ignore */
  }
  return "id-" + Math.random().toString(36).slice(2) + Date.now().toString(36);
}

function load(): PersonalizationState {
  if (loaded) return state;
  loaded = true;
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) state = { ...EMPTY, ...(JSON.parse(raw) as Partial<PersonalizationState>) };
  } catch {
    state = EMPTY;
  }
  return state;
}

function persist(next: PersonalizationState) {
  state = next;
  try {
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    /* quota / disabled — keep in-memory */
  }
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void): () => void {
  if (!loaded) load();
  listeners.add(listener);
  const onStorage = (e: StorageEvent) => {
    if (e.key === KEY) {
      loaded = false;
      load();
      listeners.forEach((l) => l());
    }
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

function getSnapshot(): PersonalizationState {
  return load();
}

function toggleIn(list: string[], id: string): string[] {
  return list.includes(id) ? list.filter((x) => x !== id) : [...list, id];
}

/** React hook exposing the personalization state and all mutators. Synced across components and browser tabs. */
export function useRegistryPersonalization() {
  const snapshot = useSyncExternalStore(subscribe, getSnapshot, () => EMPTY);

  const toggleFavorite = useCallback((id: string) => {
    persist({ ...load(), favorites: toggleIn(load().favorites, id) });
  }, []);
  const togglePin = useCallback((id: string) => {
    persist({ ...load(), pins: toggleIn(load().pins, id) });
  }, []);
  const addRecent = useCallback((id: string) => {
    const s = load();
    if (s.recent[0] === id) return;
    persist({ ...s, recent: [id, ...s.recent.filter((x) => x !== id)].slice(0, RECENT_MAX) });
  }, []);

  const createCollection = useCallback((name: string, color: string) => {
    const s = load();
    const c: Collection = { id: uid(), name, color, items: [] };
    persist({ ...s, collections: [...s.collections, c] });
    return c.id;
  }, []);
  const deleteCollection = useCallback((id: string) => {
    persist({ ...load(), collections: load().collections.filter((c) => c.id !== id) });
  }, []);
  const setInCollection = useCallback((collectionId: string, ids: string[], add: boolean) => {
    const s = load();
    persist({
      ...s,
      collections: s.collections.map((c) => {
        if (c.id !== collectionId) return c;
        const set = new Set(c.items);
        ids.forEach((id) => (add ? set.add(id) : set.delete(id)));
        return { ...c, items: [...set] };
      }),
    });
  }, []);

  const createLabel = useCallback((name: string, color: string) => {
    const s = load();
    const l: Label = { id: uid(), name, color };
    persist({ ...s, labels: [...s.labels, l] });
    return l.id;
  }, []);
  const deleteLabel = useCallback((id: string) => {
    const s = load();
    const itemLabels: Record<string, string[]> = {};
    for (const [k, v] of Object.entries(s.itemLabels)) {
      const filtered = v.filter((x) => x !== id);
      if (filtered.length) itemLabels[k] = filtered;
    }
    persist({ ...s, labels: s.labels.filter((l) => l.id !== id), itemLabels });
  }, []);
  const applyLabel = useCallback((itemIds: string[], labelId: string, add: boolean) => {
    const s = load();
    const itemLabels = { ...s.itemLabels };
    for (const id of itemIds) {
      const cur = new Set(itemLabels[id] ?? []);
      if (add) cur.add(labelId);
      else cur.delete(labelId);
      if (cur.size) itemLabels[id] = [...cur];
      else delete itemLabels[id];
    }
    persist({ ...s, itemLabels });
  }, []);

  const saveView = useCallback((name: string, query: Record<string, unknown>) => {
    const s = load();
    persist({ ...s, savedViews: [...s.savedViews, { id: uid(), name, query }] });
  }, []);
  const deleteView = useCallback((id: string) => {
    persist({ ...load(), savedViews: load().savedViews.filter((v) => v.id !== id) });
  }, []);

  return {
    state: snapshot,
    isFavorite: (id: string) => snapshot.favorites.includes(id),
    isPinned: (id: string) => snapshot.pins.includes(id),
    itemLabelIds: (id: string) => snapshot.itemLabels[id] ?? [],
    toggleFavorite,
    togglePin,
    addRecent,
    createCollection,
    deleteCollection,
    setInCollection,
    createLabel,
    deleteLabel,
    applyLabel,
    saveView,
    deleteView,
  };
}
