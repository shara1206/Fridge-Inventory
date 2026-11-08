// Mirrors the behaviour InventoryRepositoryTest, ViewModel tests and HistoryViewModelTest
// pin down on Android.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as repo from '../js/repo.js';
import { addMonths, weekStart, daysLeft, formatQuantity } from '../js/model.js';
import { guess } from '../js/shelf-life.js';

const T = '2026-07-30';

function kitchen(...names) {
  const state = repo.emptyState();
  const ids = names.map((n) => repo.addOrMerge(state, repo.draftFor(n, { purchasedOn: T })).id);
  return { state, ids };
}

test('a repeat purchase adds quantity instead of a second row', () => {
  const { state } = kitchen('Baby Spinach');
  const out = repo.addOrMerge(state, repo.draftFor('baby spinach', { purchasedOn: T }));
  assert.equal(out.kind, 'merged');
  assert.equal(repo.activeItems(state).length, 1);
  assert.equal(repo.activeItems(state)[0].quantity, 2);
});

test('merging keeps the earlier expiry and sums prices', () => {
  const state = repo.emptyState();
  repo.addOrMerge(state, { ...repo.draftFor('Milk'), expiresOn: '2026-08-10', priceCents: 300 });
  repo.addOrMerge(state, { ...repo.draftFor('Milk'), expiresOn: '2026-08-05', priceCents: 250 });
  const milk = repo.activeItems(state)[0];
  assert.equal(milk.expiresOn, '2026-08-05');
  assert.equal(milk.priceCents, 550);
});

test('the same name in a different storage area stays separate', () => {
  const state = repo.emptyState();
  repo.addOrMerge(state, { ...repo.draftFor('Peas'), storageArea: 'FRIDGE' });
  repo.addOrMerge(state, { ...repo.draftFor('Peas'), storageArea: 'FREEZER' });
  assert.equal(repo.activeItems(state).length, 2);
});

test('a draft leaves expiry empty and guesses from the name', () => {
  const d = repo.draftFor('Frozen Shrimp');
  assert.equal(d.expiresOn, null);
  assert.equal(d.category, 'SEAFOOD');
  assert.equal(d.storageArea, 'FREEZER');
});

test('eating one of several lowers the count and logs it', () => {
  const { state, ids } = kitchen('Eggs');
  repo.item(state, ids[0]).quantity = 3;
  repo.useAmount(state, ids[0], 1, T);
  assert.equal(repo.item(state, ids[0]).quantity, 2);
  assert.equal(state.events.length, 1);
  assert.equal(state.events[0].reason, 'USED');
});

test('eating the last one finishes the row as eaten', () => {
  const { state, ids } = kitchen('Milk');
  repo.useAmount(state, ids[0], 1, T);
  assert.equal(repo.item(state, ids[0]).finishedReason, 'USED');
  assert.equal(repo.activeItems(state).length, 0);
});

test('overshooting does not log food the kitchen never had', () => {
  const { state, ids } = kitchen('Pork');
  repo.item(state, ids[0]).quantity = 0.5;
  repo.useAmount(state, ids[0], 1, T);
  assert.equal(state.events[0].quantity, 0.5);
});

test('throwing out logs what was left as waste; an entry carries its share of the price', () => {
  const { state, ids } = kitchen('Kale');
  Object.assign(repo.item(state, ids[0]), { quantity: 2, priceCents: 400 });
  repo.useAmount(state, ids[0], 1, T);
  repo.markFinished(state, ids[0], 'DISCARDED', T);
  const eaten = state.events.find((e) => e.reason === 'USED');
  const binned = state.events.find((e) => e.reason === 'DISCARDED');
  assert.equal(eaten.valueCents, 200);
  assert.equal(binned.quantity, 1);
  // As on Android, the row's price is the whole receipt line and is not reduced by partial
  // use, so the rest is valued against it. Rough by design.
  assert.equal(binned.valueCents, 400);
  assert.equal(repo.markFinished(state, ids[0], 'DISCARDED', T), null, 'no double log');
});

test('undo deletes the entry and brings an emptied row back', () => {
  const { state, ids } = kitchen('Milk');
  const eventId = repo.useAmount(state, ids[0], 1, T);
  repo.undoConsumption(state, eventId);
  assert.equal(state.events.length, 0);
  assert.equal(repo.item(state, ids[0]).finishedOn, null);
  assert.equal(repo.item(state, ids[0]).quantity, 1);
});

test('undo after a partial use puts back only that entry', () => {
  const { state, ids } = kitchen('Rice');
  repo.item(state, ids[0]).quantity = 3;
  const first = repo.useAmount(state, ids[0], 1, T);
  repo.useAmount(state, ids[0], 1, T);
  repo.undoConsumption(state, first);
  assert.equal(repo.item(state, ids[0]).quantity, 2);
  assert.equal(state.events.length, 1);
});

test('buying another moves the purchase date, keeps expiry, logs nothing', () => {
  const state = repo.emptyState();
  const { id } = repo.addOrMerge(state, {
    ...repo.draftFor('Rice', { purchasedOn: '2026-07-01' }), expiresOn: '2026-12-01',
  });
  repo.addOne(state, id, T);
  const rice = repo.item(state, id);
  assert.deepEqual([rice.quantity, rice.purchasedOn, rice.expiresOn], [2, T, '2026-12-01']);
  assert.equal(state.events.length, 0);
});

test('editing a count is a correction and logs nothing', () => {
  const { state, ids } = kitchen('Rice');
  repo.save(state, { ...repo.item(state, ids[0]), quantity: 5 });
  assert.equal(repo.item(state, ids[0]).quantity, 5);
  assert.equal(state.events.length, 0);
});

test('the log keeps what the food was after a rename', () => {
  const { state, ids } = kitchen('Bok Choy');
  repo.item(state, ids[0]).quantity = 2;
  repo.useAmount(state, ids[0], 1, T);
  repo.save(state, { ...repo.item(state, ids[0]), name: 'Shanghai Bok Choy' });
  assert.equal(state.events[0].name, 'Bok Choy');
});

test('a receipt merges into the kitchen and keeps its raw text', () => {
  const { state } = kitchen('Napa Cabbage');
  const receipt = { store: 'HMART', purchasedOn: T, totalCents: 500, rawText: 'H MART ...',
    lines: [] };
  const line = { name: 'Napa Cabbage', quantity: 1, priceCents: 257 };
  repo.commitReceipt(state, receipt, [
    { line, name: 'Napa Cabbage' },
    { line: { ...line, name: 'Mandu' }, name: 'Frozen Mandu', storageArea: 'FREEZER' },
  ]);
  assert.equal(repo.activeItems(state).length, 2);
  assert.equal(state.purchases[0].rawText, 'H MART ...');
});

test('a failed restore is the caller’s to roll back: replaceAll swaps everything', () => {
  const { state } = kitchen('Tofu');
  repo.replaceAll(state, [repo.draftFor('Spinach')], [{ name: 'Milk', reason: 'USED',
    quantity: 1, occurredOn: T, itemId: 9 }]);
  assert.deepEqual(state.items.map((i) => i.name), ['Spinach']);
  assert.equal(state.events[0].itemId, null);
});

test('merge import appends history and folds live rows', () => {
  const { state } = kitchen('Tofu');
  repo.mergeImport(state, [repo.draftFor('Tofu'), { ...repo.draftFor('Old'), finishedOn: T,
    finishedReason: 'USED' }], []);
  assert.equal(repo.activeItems(state).length, 1);
  assert.equal(repo.activeItems(state)[0].quantity, 2);
  assert.equal(state.items.length, 2);
});

test('history groups into Monday weeks and filters by reason', () => {
  const events = [
    { id: 1, name: 'Milk', occurredOn: '2026-11-08', reason: 'USED', quantity: 1, recordedAt: 0 },
    { id: 2, name: 'Eggs', occurredOn: '2026-11-02', reason: 'DISCARDED', quantity: 2,
      recordedAt: 0 },
    { id: 3, name: 'Rice', occurredOn: '2026-11-01', reason: null, quantity: 1, recordedAt: 0 },
  ];
  const weeks = repo.groupByWeek(events);
  assert.deepEqual(weeks.map((w) => w.start), ['2026-11-02', '2026-10-26']);
  assert.equal(weeks[0].end, '2026-11-08');
  assert.equal(weeks[0].eatenQuantity, 1);
  assert.equal(weeks[0].thrownQuantity, 2);
  assert.deepEqual(events.filter((e) => repo.acceptsFilter('EATEN', e)).map((e) => e.id), [1]);
  assert.deepEqual(events.filter((e) => repo.acceptsFilter('ALL', e)).length, 3);
});

test('waste report counts only the last 30 days and ignores unknown reasons', () => {
  const state = repo.emptyState();
  state.events = [
    { name: 'A', reason: 'USED', quantity: 3, occurredOn: '2026-07-20', category: 'FRUIT',
      store: 'OTHER' },
    { name: 'B', reason: 'DISCARDED', quantity: 1, occurredOn: '2026-07-21', valueCents: 199,
      category: 'VEGETABLES', store: 'HMART' },
    { name: 'C', reason: null, quantity: 5, occurredOn: '2026-07-22', category: 'OTHER',
      store: 'OTHER' },
    { name: 'D', reason: 'DISCARDED', quantity: 9, occurredOn: '2026-05-01', category: 'MEAT',
      store: 'OTHER' },
  ];
  const r = repo.wasteReport(state, 30, T);
  assert.equal(r.wastePercent, 25);
  assert.equal(r.discardedCents, 199);
  assert.deepEqual(r.discardedByCategory, [['VEGETABLES', 1]]);
});

test('dates: months clamp, weeks start Monday, days left can be negative', () => {
  assert.equal(addMonths('2026-01-31', 1), '2026-02-28');
  assert.equal(weekStart('2026-11-04'), '2026-11-02');
  assert.equal(weekStart('2026-11-02'), '2026-11-02');
  assert.equal(daysLeft({ expiresOn: '2026-07-29' }, T), -1);
  assert.equal(daysLeft({ expiresOn: null }, T), null);
  assert.equal(formatQuantity(2), '2');
  assert.equal(formatQuantity(0.5), '0.5');
});

test('shelf life: longest keyword wins and frozen moves to the freezer', () => {
  assert.equal(guess('Chicken Thigh').category, 'MEAT');
  assert.equal(guess('Sweet Potato').storageArea, 'PANTRY');
  assert.equal(guess('Kimchi').storageArea, 'FRIDGE');
  assert.equal(guess('mystery').category, 'OTHER');
});

test('backup reminder: same rule as Android', () => {
  assert.equal(repo.backupReminder(repo.emptyState(), T), null, 'nothing to lose yet');
  const { state } = kitchen('Milk');
  assert.deepEqual(repo.backupReminder(state, T), { lastBackupOn: null });
  state.lastBackupOn = '2026-07-17'; // 13 days before T
  assert.equal(repo.backupReminder(state, T), null);
  state.lastBackupOn = '2026-07-16'; // 14 days
  assert.deepEqual(repo.backupReminder(state, T), { lastBackupOn: '2026-07-16' });
});
