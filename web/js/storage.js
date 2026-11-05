// The whole kitchen is one IndexedDB record, written in a single put after every change.
// One put is atomic, so a change that touches several things (finish a row and log it,
// restore a backup) lands completely or not at all. A household's data is small enough that
// rewriting it each time costs nothing noticeable.

import { emptyState } from './repo.js';

const DB = 'fridge-inventory';
const STORE = 'kv';
const KEY = 'state';
const PREFS = 'prefs';

function open() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

let dbPromise = null;
function db() {
  dbPromise ??= open();
  return dbPromise;
}

function run(mode, action) {
  return db().then((database) => new Promise((resolve, reject) => {
    const tx = database.transaction(STORE, mode);
    const request = action(tx.objectStore(STORE));
    tx.oncomplete = () => resolve(request?.result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  }));
}

export async function loadState() {
  const saved = await run('readonly', (s) => s.get(KEY));
  return saved ? { ...emptyState(), ...saved } : emptyState();
}

export function saveState(state) {
  return run('readwrite', (s) => s.put(state, KEY));
}

export async function loadPrefs() {
  return (await run('readonly', (s) => s.get(PREFS))) ?? { lang: 'auto' };
}

export function savePrefs(prefs) {
  return run('readwrite', (s) => s.put(prefs, PREFS));
}

/** Asks the browser not to evict the data under storage pressure. */
export async function requestPersistence() {
  try {
    if (!navigator.storage?.persist) return false;
    if (await navigator.storage.persisted()) return true;
    return await navigator.storage.persist();
  } catch {
    return false;
  }
}
