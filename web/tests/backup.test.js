import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { decode, encode, BackupFormatError } from '../js/backup.js';

const fixture = (name) => readFileSync(new URL(`./fixtures/${name}`, import.meta.url), 'utf8');

test('reads a backup written by the Android app', () => {
  const { items, events } = decode(fixture('android-backup.json'));
  assert.ok(items.length > 0);
  assert.ok(events.length > 0);
  const finished = items.find((i) => i.finishedOn);
  assert.ok(finished.finishedReason);
  assert.ok(events.every((e) => e.itemId === null));
});

test('round-trips through its own format', () => {
  const { items, events } = decode(fixture('android-backup.json'));
  const again = decode(encode(items, events));
  assert.deepEqual(again.items, items);
  assert.deepEqual(again.events, events);
});

test('unknown enum values decay instead of failing', () => {
  const text = JSON.stringify({
    format: 'fridge-inventory', version: 2,
    items: [{ name: 'Thing', category: 'NOPE', storageArea: 'garage', store: 'COSTCO',
      quantity: -3, purchasedOn: 'yesterday' }],
  });
  const [it] = decode(text).items;
  assert.deepEqual([it.category, it.storageArea, it.store, it.quantity],
    ['OTHER', 'FRIDGE', 'OTHER', 1]);
  assert.match(it.purchasedOn, /^\d{4}-\d{2}-\d{2}$/);
});

test('a version 1 file has no log and still reads', () => {
  const text = JSON.stringify({ format: 'fridge-inventory', version: 1,
    items: [{ name: 'Milk' }] });
  assert.deepEqual(decode(text).events, []);
});

test('rejects other files and newer versions', () => {
  assert.throws(() => decode('not json'), BackupFormatError);
  assert.throws(() => decode('{"format":"other"}'), BackupFormatError);
  assert.throws(() => decode('{"format":"fridge-inventory","version":99,"items":[]}'),
    BackupFormatError);
});
