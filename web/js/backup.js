// Port of data/backup/InventoryBackup.kt. The file format is shared with the Android app
// byte for byte in meaning, so a backup made on either phone restores on the other.
//
// Unknown enum values decay to their neutral member instead of failing the import: losing a
// category label is a much smaller loss than losing the file.

import { CATEGORIES, FINISH_REASONS, STORAGE_AREAS, STORES, isIsoDate, today } from './model.js';

export const FORMAT = 'fridge-inventory';
export const VERSION = 2;

export class BackupFormatError extends Error {}

export function encode(items, events, exportedOn = today()) {
  return JSON.stringify({
    format: FORMAT,
    version: VERSION,
    exportedOn,
    items: items.map((i) => ({
      name: i.name,
      category: i.category,
      storageArea: i.storageArea,
      quantity: i.quantity,
      unit: i.unit ?? 'ea',
      purchasedOn: i.purchasedOn,
      expiresOn: i.expiresOn ?? null,
      store: i.store,
      priceCents: i.priceCents ?? null,
      notes: i.notes ?? '',
      finishedOn: i.finishedOn ?? null,
      finishedReason: i.finishedReason ?? null,
    })),
    events: events.map((e) => ({
      name: e.name,
      category: e.category,
      storageArea: e.storageArea,
      store: e.store,
      quantity: e.quantity,
      reason: e.reason ?? null,
      occurredOn: e.occurredOn,
      recordedAt: e.recordedAt ?? 0,
      valueCents: e.valueCents ?? null,
    })),
  }, null, 2);
}

function pick(raw, values, fallback) {
  if (typeof raw !== 'string') return fallback;
  return values.find((v) => v.toLowerCase() === raw.toLowerCase()) ?? fallback;
}

function date(raw) {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  return isIsoDate(trimmed) ? trimmed : null;
}

function positive(raw) {
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : 1;
}

function intOrNull(raw) {
  if (raw == null) return null;
  const n = Number(raw);
  return Number.isFinite(n) ? Math.trunc(n) : null;
}

function name(raw) {
  return typeof raw === 'string' ? raw.trim() : '';
}

/**
 * @returns {{items: object[], events: object[]}} without ids; the receiving store assigns
 * them, since ids from another install would collide.
 */
export function decode(text) {
  let root;
  try {
    root = JSON.parse(text);
  } catch (e) {
    throw new BackupFormatError(`Not a JSON document: ${e.message}`);
  }
  if (!root || root.format !== FORMAT) throw new BackupFormatError(`Not a ${FORMAT} backup`);
  if ((Number(root.version) || 0) > VERSION) {
    throw new BackupFormatError('Backup was written by a newer version of the app');
  }
  if (!Array.isArray(root.items)) throw new BackupFormatError('Backup has no items');

  const storeKeys = Object.keys(STORES);
  const items = root.items
    .filter((j) => j && typeof j === 'object' && name(j.name) !== '')
    .map((j) => ({
      name: name(j.name),
      category: pick(j.category, CATEGORIES, 'OTHER'),
      storageArea: pick(j.storageArea, STORAGE_AREAS, 'FRIDGE'),
      quantity: positive(j.quantity),
      unit: typeof j.unit === 'string' && j.unit.trim() ? j.unit : 'ea',
      purchasedOn: date(j.purchasedOn) ?? today(),
      expiresOn: date(j.expiresOn),
      store: pick(j.store, storeKeys, 'OTHER'),
      priceCents: intOrNull(j.priceCents),
      notes: typeof j.notes === 'string' ? j.notes : '',
      finishedOn: date(j.finishedOn),
      finishedReason: j.finishedReason == null ? null : pick(j.finishedReason, FINISH_REASONS, 'USED'),
      purchaseId: null,
    }));

  // itemId is dropped: it pointed at a row in the database that wrote the file.
  const events = (Array.isArray(root.events) ? root.events : [])
    .filter((j) => j && typeof j === 'object' && name(j.name) !== '')
    .map((j) => ({
      itemId: null,
      name: name(j.name),
      category: pick(j.category, CATEGORIES, 'OTHER'),
      storageArea: pick(j.storageArea, STORAGE_AREAS, 'FRIDGE'),
      store: pick(j.store, storeKeys, 'OTHER'),
      quantity: positive(j.quantity),
      reason: j.reason == null ? null : pick(j.reason, FINISH_REASONS, 'USED'),
      occurredOn: date(j.occurredOn) ?? today(),
      recordedAt: Number(j.recordedAt) || 0,
      valueCents: intOrNull(j.valueCents),
    }));

  return { items, events };
}
