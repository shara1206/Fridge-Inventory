// Caches the app shell so it opens with no connection. The app never fetches data from
// anywhere; this only serves its own files. Bump VERSION whenever a file below changes, so
// phones pick up the new copy on their next visit.
const VERSION = 'fridge-v2';
const FILES = [
  './',
  './index.html',
  './styles.css',
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
  './icons/apple-touch-icon.png',
  './js/app.js',
  './js/backup.js',
  './js/i18n.js',
  './js/labels.js',
  './js/model.js',
  './js/parsers.js',
  './js/receipt-text.js',
  './js/repo.js',
  './js/shelf-life.js',
  './js/storage.js',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(VERSION).then((cache) => cache.addAll(FILES)));
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

// Cache first: the app must open offline. A new VERSION replaces the cache on update.
self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    caches.match(event.request, { ignoreSearch: true })
      .then((hit) => hit ?? fetch(event.request)),
  );
});
