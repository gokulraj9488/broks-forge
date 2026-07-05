/**
 * Shared "last user activity" clock backing the idle-session timeout.
 *
 * Kept in localStorage so activity in one tab keeps every tab alive, and so
 * the axios request interceptor (plain module code) can bump it without any
 * React coupling. Falls back to an in-memory value when storage is blocked.
 */
const STORAGE_KEY = "broksforge.lastActivityAt";

let inMemoryLast = Date.now();

export function touchActivity(): void {
  inMemoryLast = Date.now();
  try {
    localStorage.setItem(STORAGE_KEY, String(inMemoryLast));
  } catch {
    // Storage unavailable (SSR, privacy mode) — the in-memory clock still works.
  }
}

export function getLastActivity(): number {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = Number(raw);
      if (Number.isFinite(parsed)) return Math.max(parsed, inMemoryLast);
    }
  } catch {
    // fall through to the in-memory clock
  }
  return inMemoryLast;
}

export function clearActivity(): void {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}
