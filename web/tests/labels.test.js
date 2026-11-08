// Mirrors app/src/test/.../data/labels/LabelSheetTest.kt.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { labelSheet, labelLine, PRINTED_ZONES } from '../js/labels.js';
import { CATEGORIES } from '../js/model.js';

const item = (name, category, storageArea = 'FRIDGE', { quantity = 1, finished = false } = {}) => ({
  name, category, storageArea, quantity, finishedOn: finished ? '2026-07-01' : null,
});

test('an empty kitchen still prints the ten fixed cards, empty, on one page', () => {
  const sheet = labelSheet([]);
  assert.equal(sheet.zones.length, PRINTED_ZONES.length);
  assert.ok(sheet.zones.every((z) => z.printed && z.items.length === 0));
  assert.equal(sheet.pageCount, 1);
});

test('items land on the card for their category and area', () => {
  const sheet = labelSheet([
    item('Ribeye', 'MEAT', 'FREEZER'),
    item('Dumplings', 'PREPARED', 'FREEZER'),
    item('Century egg', 'PREPARED', 'FRIDGE'),
  ]);
  const card = (c, a) => sheet.zones.find((z) => z.category === c && z.storageArea === a);
  assert.deepEqual(card('PREPARED', 'FREEZER').items.map((i) => i.name), ['Dumplings']);
  assert.deepEqual(card('PREPARED', 'FRIDGE').items.map((i) => i.name), ['Century egg']);
});

test('tofu has its own printed card and seasoning does not', () => {
  const sheet = labelSheet([item('Firm tofu', 'TOFU_SOY'), item('Doubanjiang', 'SEASONING')]);
  assert.ok(sheet.printed.find((z) => z.category === 'TOFU_SOY').items.length > 0);
  assert.deepEqual(sheet.extra.map((z) => z.category), ['SEASONING']);
});

test('uncovered categories become extra cards grouped by area', () => {
  const sheet = labelSheet([
    item('Rice', 'GRAINS_NOODLES', 'PANTRY'),
    item('Oolong', 'BEVERAGE', 'FRIDGE'),
    item('Sparkling water', 'BEVERAGE', 'PANTRY'),
  ]);
  assert.deepEqual(sheet.extra.map((z) => `${z.category}/${z.storageArea}`),
    ['BEVERAGE/FRIDGE', 'GRAINS_NOODLES/PANTRY', 'BEVERAGE/PANTRY']);
  assert.ok(sheet.extra.every((z) => !z.printed));
  assert.equal(sheet.itemCount, 3);
});

test('finished items are left off', () => {
  const sheet = labelSheet([item('Spinach', 'VEGETABLES'),
    item('Old spinach', 'VEGETABLES', 'FRIDGE', { finished: true })]);
  assert.deepEqual(sheet.zones.flatMap((z) => z.items).map((i) => i.name), ['Spinach']);
});

test('extra cards spill onto further pages ten at a time', () => {
  const sheet = labelSheet(CATEGORIES.map((c) => item(`x-${c}`, c, 'PANTRY')));
  assert.equal(sheet.extra.length, CATEGORIES.length);
  assert.equal(sheet.pageCount, 3);
});

test('a count of one is left off the line', () => {
  assert.equal(labelLine(item('Milk', 'DAIRY_EGGS')), 'Milk');
  assert.equal(labelLine(item('Milk', 'DAIRY_EGGS', 'FRIDGE', { quantity: 2 })), 'Milk ×2');
  assert.equal(labelLine(item('Chicken', 'MEAT', 'FRIDGE', { quantity: 1.5 })), 'Chicken ×1.5');
});
