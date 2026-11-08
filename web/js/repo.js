// Port of data/repo/InventoryRepository.kt and WasteReport.kt, over a plain state object.
//
// Every function takes the whole state and changes it in place; the caller saves it in one
// write afterwards, which is what makes a multi-step change (finish + log, restore) atomic.
// Nothing here touches the DOM or storage, so it all tests in Node.

import { guess } from './shelf-life.js';
import { CATEGORIES, STORES, addDays, daysBetween, today, weekStart } from './model.js';

export function emptyState() {
  return { nextId: 1, items: [], events: [], purchases: [], lastBackupOn: null };
}

function newId(state) {
  return state.nextId++;
}

export const isFinished = (item) => item.finishedOn != null;

/** Still in the kitchen, soonest to expire first, untracked expiry last, then by name. */
export function activeItems(state) {
  return state.items.filter((i) => !isFinished(i)).sort((a, b) => {
    if ((a.expiresOn == null) !== (b.expiresOn == null)) return a.expiresOn == null ? 1 : -1;
    if (a.expiresOn !== b.expiresOn) return a.expiresOn < b.expiresOn ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
}

export function item(state, id) {
  return state.items.find((i) => i.id === id) ?? null;
}

/**
 * A new item with category and storage area guessed from the name. Expiry stays empty: a
 * guessed date reads as a fact once it sits in the field.
 */
export function draftFor(name, {
  purchasedOn = today(), store = 'OTHER', quantity = 1, priceCents = null, purchaseId = null,
} = {}) {
  const g = guess(name);
  return {
    id: 0,
    name,
    category: g.category,
    storageArea: g.storageArea,
    quantity,
    unit: 'ea',
    purchasedOn,
    expiresOn: null,
    store,
    priceCents,
    notes: '',
    finishedOn: null,
    finishedReason: null,
    purchaseId,
  };
}

function sumPrices(a, b) {
  return a == null && b == null ? null : (a ?? 0) + (b ?? 0);
}

/**
 * Inserts the item, or folds it into an identical row already in the same storage area.
 * The survivor keeps the earlier expiry: the older stock is the one to eat first.
 * @returns {{kind: 'inserted'|'merged', id: number}}
 */
export function addOrMerge(state, newItem) {
  const key = newItem.name.trim().toLowerCase();
  const existing = activeItems(state).find(
    (i) => i.storageArea === newItem.storageArea && i.name.trim().toLowerCase() === key,
  );
  if (!existing) {
    const id = newId(state);
    state.items.push({ ...newItem, id });
    return { kind: 'inserted', id };
  }
  const expiries = [existing.expiresOn, newItem.expiresOn].filter(Boolean).sort();
  Object.assign(existing, {
    quantity: existing.quantity + newItem.quantity,
    expiresOn: expiries[0] ?? null,
    priceCents: sumPrices(existing.priceCents, newItem.priceCents),
    purchasedOn: existing.purchasedOn > newItem.purchasedOn
      ? existing.purchasedOn : newItem.purchasedOn,
  });
  return { kind: 'merged', id: existing.id };
}

/** Manual save. New items go through the merge path; edits replace the row in place. */
export function save(state, edited) {
  if (!edited.id) return addOrMerge(state, edited);
  const index = state.items.findIndex((i) => i.id === edited.id);
  if (index < 0) return addOrMerge(state, { ...edited, id: 0 });
  state.items[index] = { ...state.items[index], ...edited };
  return { kind: 'updated', id: edited.id };
}

/** Removes a row entirely, writing no history. For mistakes, not for food that left. */
export function deleteItem(state, id) {
  state.items = state.items.filter((i) => i.id !== id);
}

/** What [part] of a row costing [priceCents] for [whole] units is worth. */
function shareOfPrice(priceCents, whole, part) {
  if (priceCents == null) return null;
  if (whole <= 0.0001) return priceCents;
  return Math.round(priceCents * (Math.min(part, whole) / whole));
}

/** Snapshots the item into the log, so renaming the item later does not rewrite history. */
function record(state, it, quantity, reason, on) {
  const id = newId(state);
  state.events.push({
    id,
    itemId: it.id,
    name: it.name,
    category: it.category,
    storageArea: it.storageArea,
    store: it.store,
    quantity,
    reason,
    occurredOn: on,
    recordedAt: Date.now(),
    valueCents: shareOfPrice(it.priceCents, it.quantity, quantity),
  });
  return id;
}

/**
 * Clears the row out, logging whatever was left on it as eaten or binned.
 * @returns the logged event id for undo, or null if there was nothing to finish.
 */
export function markFinished(state, id, reason, on = today()) {
  const it = item(state, id);
  if (!it || isFinished(it)) return null;
  const eventId = record(state, it, it.quantity, reason, on);
  it.finishedOn = on;
  it.finishedReason = reason;
  return eventId;
}

/**
 * Eats [amount] of an item. Every call logs, including the ones that leave food behind.
 * Reaching zero finishes the row as eaten rather than leaving a 0-quantity ghost; the
 * logged amount is clamped so overshooting cannot report food the kitchen never had.
 */
export function useAmount(state, id, amount, on = today()) {
  const it = item(state, id);
  if (!it || isFinished(it)) return null;
  const eventId = record(state, it, Math.min(amount, it.quantity), 'USED', on);
  const remaining = it.quantity - amount;
  if (remaining > 0.0001) {
    it.quantity = remaining;
  } else {
    it.finishedOn = on;
    it.finishedReason = 'USED';
  }
  return eventId;
}

/**
 * Takes one entry back out of the log and puts its amount back on the row. The entry is
 * deleted, not cancelled out: the log reads as what happened, not as a ledger.
 */
export function undoConsumption(state, eventId) {
  const event = state.events.find((e) => e.id === eventId);
  if (!event) return;
  const it = event.itemId != null ? item(state, event.itemId) : null;
  if (it) {
    if (isFinished(it)) {
      it.finishedOn = null;
      it.finishedReason = null;
      it.quantity = event.quantity;
    } else {
      it.quantity += event.quantity;
    }
  }
  state.events = state.events.filter((e) => e.id !== eventId);
}

/** Bought another one: a purchase, so the purchase date moves; expiry is left alone. */
export function addOne(state, id, on = today()) {
  const it = item(state, id);
  if (!it) return;
  it.quantity += 1;
  if (on > it.purchasedOn) it.purchasedOn = on;
}

/** Persists a reviewed receipt and one item per kept line, merging into existing rows. */
export function commitReceipt(state, receipt, entries) {
  const purchaseId = newId(state);
  const purchasedOn = receipt.purchasedOn ?? today();
  state.purchases.push({
    id: purchaseId,
    store: receipt.store,
    purchasedOn,
    totalCents: receipt.totalCents,
    rawText: receipt.rawText,
  });
  for (const entry of entries) {
    const draft = draftFor(entry.name, {
      purchasedOn,
      store: receipt.store,
      quantity: entry.line.quantity,
      priceCents: entry.line.priceCents,
      purchaseId,
    });
    addOrMerge(state, entry.storageArea ? { ...draft, storageArea: entry.storageArea } : draft);
  }
  return purchaseId;
}

// ---- backup ------------------------------------------------------------------

/** Restores a backup wholesale; the log goes too, since its item ids would dangle. */
export function replaceAll(state, items, events) {
  state.items = [];
  state.events = [];
  for (const it of items) state.items.push({ ...it, id: newId(state) });
  for (const e of events) state.events.push({ ...e, id: newId(state), itemId: null });
}

/**
 * Folds a backup into the current kitchen. Finished rows and log entries are history and are
 * appended; live rows merge like a repeat purchase.
 */
export function mergeImport(state, items, events) {
  for (const it of items) {
    if (isFinished(it)) state.items.push({ ...it, id: newId(state) });
    else addOrMerge(state, { ...it, id: 0 });
  }
  for (const e of events) state.events.push({ ...e, id: newId(state), itemId: null });
}

// ---- backup reminder -----------------------------------------------------------

/** Same rule as Android's InventoryViewModel.backupReminder. */
export const BACKUP_REMIND_DAYS = 14;

/**
 * @returns null when no nudge is due (nothing in the kitchen, or a backup in the last two
 * weeks), else { lastBackupOn } with null meaning never backed up.
 */
export function backupReminder(state, on = today()) {
  if (activeItems(state).length === 0) return null;
  const last = state.lastBackupOn;
  if (last && daysBetween(last, on) < BACKUP_REMIND_DAYS) return null;
  return { lastBackupOn: last ?? null };
}

// ---- history -----------------------------------------------------------------

/** The whole log, newest first. */
export function history(state) {
  return [...state.events].sort((a, b) => {
    if (a.occurredOn !== b.occurredOn) return a.occurredOn < b.occurredOn ? 1 : -1;
    if (a.recordedAt !== b.recordedAt) return b.recordedAt - a.recordedAt;
    return b.id - a.id;
  });
}

/** Entries with no recorded reason only show under ALL rather than being guessed. */
export function acceptsFilter(filter, event) {
  if (filter === 'EATEN') return event.reason === 'USED';
  if (filter === 'THROWN_OUT') return event.reason === 'DISCARDED';
  return true;
}

/** Monday-to-Sunday weeks by the day the food left, newest week first. */
export function groupByWeek(events) {
  const weeks = new Map();
  for (const e of events) {
    const start = weekStart(e.occurredOn);
    if (!weeks.has(start)) weeks.set(start, []);
    weeks.get(start).push(e);
  }
  return [...weeks.entries()]
    .sort(([a], [b]) => (a < b ? 1 : -1))
    .map(([start, list]) => ({
      start,
      end: addDays(start, 6),
      events: list,
      eatenQuantity: sum(list.filter((e) => e.reason === 'USED')),
      thrownQuantity: sum(list.filter((e) => e.reason === 'DISCARDED')),
    }));
}

function sum(events) {
  return events.reduce((total, e) => total + e.quantity, 0);
}

/** Waste over the last [days] days, measured in what was consumed. */
export function wasteReport(state, days = 30, on = today()) {
  const since = addDays(on, -days);
  const events = state.events.filter((e) => e.occurredOn >= since);
  const discarded = events.filter((e) => e.reason === 'DISCARDED');
  const used = sum(events.filter((e) => e.reason === 'USED'));
  const thrown = sum(discarded);
  const known = used + thrown;
  const rank = (keyOf, order) => {
    const totals = new Map();
    for (const e of discarded) totals.set(keyOf(e), (totals.get(keyOf(e)) ?? 0) + e.quantity);
    return [...totals.entries()]
      .sort((a, b) => b[1] - a[1] || order.indexOf(a[0]) - order.indexOf(b[0]));
  };
  return {
    usedQuantity: used,
    discardedQuantity: thrown,
    discardedCents: discarded.reduce((t, e) => t + (e.valueCents ?? 0), 0),
    wastePercent: known <= 0.0001 ? null : Math.round((thrown * 100) / known),
    discardedByCategory: rank((e) => e.category, CATEGORIES),
    discardedByStore: rank((e) => e.store, Object.keys(STORES)),
  };
}
