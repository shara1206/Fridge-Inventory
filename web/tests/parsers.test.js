// Mirrors app/src/test/.../receipt/ParserTest.kt case for case.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseReceipt } from '../js/parsers.js';
import * as S from './sample-receipts.js';

const names = (receipt) => receipt.lines.map((l) => l.name);

test('detects each store', () => {
  assert.equal(parseReceipt(S.H_MART).store, 'HMART');
  assert.equal(parseReceipt(S.TT).store, 'TT');
  assert.equal(parseReceipt(S.TRADER_JOES).store, 'TRADER_JOES');
  assert.equal(parseReceipt(S.WHOLE_FOODS).store, 'WHOLE_FOODS');
  assert.equal(parseReceipt(S.UNKNOWN_STORE).store, 'OTHER');
});

test('drops totals taxes and payment lines', () => {
  const forbidden = ['total', 'tax', 'visa', 'gst', 'savings', 'interac', 'items'];
  for (const text of [S.H_MART, S.TT, S.TRADER_JOES, S.WHOLE_FOODS, S.UNKNOWN_STORE]) {
    for (const name of names(parseReceipt(text))) {
      for (const word of forbidden) {
        assert.ok(!name.toLowerCase().includes(word), `parsed a noise line as an item: '${name}'`);
      }
    }
  }
});

test('hmart strips upc codes and expands abbreviations', () => {
  const r = parseReceipt(S.H_MART);
  assert.equal(r.lines.length, 4);
  assert.ok(names(r).includes('Organic Spinach 5oz'));
  assert.ok(names(r).includes('Pork Belly Slice'));
  assert.ok(names(r).includes('Napa Cabbage'));
});

test('hmart reads date and total', () => {
  const r = parseReceipt(S.H_MART);
  assert.equal(r.purchasedOn, '2026-04-12');
  assert.equal(r.totalCents, 2319);
});

test('hmart applies weight line to the preceding item', () => {
  const pork = parseReceipt(S.H_MART).lines.find((l) => l.name.startsWith('Pork Belly'));
  assert.ok(Math.abs(pork.quantity - 0.86) < 0.001);
  assert.equal(pork.priceCents, 1245);
});

test('tt keeps chinese names and strips tax flags', () => {
  const r = parseReceipt(S.TT);
  assert.equal(r.lines.length, 3);
  assert.ok(names(r).some((n) => n.includes('上海青') && n.includes('Bok Choy')));
  assert.equal(r.lines.find((l) => l.name.includes('上海青')).priceCents, 329);
});

test('tt reads iso date and count multiplier', () => {
  const r = parseReceipt(S.TT);
  assert.equal(r.purchasedOn, '2026-04-15');
  assert.equal(r.lines.find((l) => l.name.includes('Dumpling')).quantity, 2);
});

test('trader joes handles two digit year and quantity', () => {
  const r = parseReceipt(S.TRADER_JOES);
  assert.equal(r.purchasedOn, '2026-04-20');
  assert.equal(r.lines.length, 3);
  assert.equal(r.lines.find((l) => l.name.includes('Chicken')).quantity, 2);
  assert.ok(names(r).some((n) => n.startsWith('Unsweetened Vanilla')));
});

test('whole foods keeps 365 brand and drops prime savings', () => {
  const r = parseReceipt(S.WHOLE_FOODS);
  assert.equal(r.lines.length, 3);
  assert.ok(names(r).some((n) => n.startsWith('365 Organic Whole Milk')));
  assert.ok(names(r).every((n) => !n.includes('Prime')));
  assert.ok(Math.abs(r.lines.find((l) => l.name.includes('Chicken')).quantity - 1.42) < 0.001);
});

test('generic parser still reads an unknown receipt', () => {
  const r = parseReceipt(S.UNKNOWN_STORE);
  assert.equal(r.lines.length, 2);
  assert.equal(r.totalCents, 467);
  assert.ok(r.purchasedOn);
});

test('blank text yields no lines', () => {
  assert.equal(parseReceipt('').lines.length, 0);
});

test('windows line endings from a paste parse the same', () => {
  const r = parseReceipt(S.H_MART.replace(/\n/g, '\r\n'));
  assert.equal(r.lines.length, 4);
});
