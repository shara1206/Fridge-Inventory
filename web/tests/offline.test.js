// The service worker can only serve offline what it precached. A file added to the app but
// not to this list would be missing the first time the phone opens the app without a
// connection, so the list is checked against what is actually on disk.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const sw = readFileSync(new URL('sw.js', root), 'utf8');
const listed = new Set([...sw.matchAll(/'\.\/([^']*)'/g)].map((m) => m[1]).filter(Boolean));

test('every app file is precached for offline use', () => {
  const onDisk = [
    'index.html', 'styles.css', 'manifest.webmanifest',
    ...readdirSync(new URL('js/', root)).map((f) => `js/${f}`),
    ...readdirSync(new URL('icons/', root)).map((f) => `icons/${f}`),
  ];
  const missing = onDisk.filter((f) => !listed.has(f));
  assert.deepEqual(missing, [], 'add these to FILES in sw.js and bump VERSION');
});

test('every precached file exists', () => {
  for (const f of listed) assert.doesNotThrow(() => readFileSync(new URL(f, root)), f);
});
