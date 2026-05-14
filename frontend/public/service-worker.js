const CACHE_VERSION = 'exprivia-booking-v3';
const APP_SHELL_CACHE = `${CACHE_VERSION}-shell`;
const STATIC_CACHE = `${CACHE_VERSION}-static`;

const APP_SHELL_URLS = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/favicon.ico',
  '/assets/logo-exprivia.svg',
  '/icons/favicon-192.png',
  '/icons/favicon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(APP_SHELL_CACHE)
      .then((cache) => cache.addAll(APP_SHELL_URLS))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => Promise.all(
        cacheNames
          .filter((cacheName) => !cacheName.startsWith(CACHE_VERSION))
          .map((cacheName) => caches.delete(cacheName)),
      ))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;

  if (request.method !== 'GET') {
    return;
  }

  const requestUrl = new URL(request.url);
  const isSameOrigin = requestUrl.origin === self.location.origin;
  const isApiRequest = requestUrl.pathname.startsWith('/api-')
    || requestUrl.pathname.startsWith('/api/');

  if (request.mode === 'navigate' && isSameOrigin) {
    event.respondWith(networkFirstNavigation(request));
    return;
  }

  if (!isSameOrigin || isApiRequest) {
    return;
  }

  event.respondWith(staleWhileRevalidate(request));
});

async function networkFirstNavigation(request) {
  try {
    const networkResponse = await fetch(request);
    const cache = await caches.open(APP_SHELL_CACHE);
    cache.put('/index.html', networkResponse.clone());
    return networkResponse;
  } catch {
    const cachedResponse = await caches.match('/index.html');
    return cachedResponse ?? new Response('Applicazione non disponibile offline.', {
      status: 503,
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    });
  }
}

async function staleWhileRevalidate(request) {
  const cache = await caches.open(STATIC_CACHE);
  const cachedResponse = await cache.match(request);
  const networkResponsePromise = fetch(request)
    .then((networkResponse) => {
      if (networkResponse.ok) {
        cache.put(request, networkResponse.clone());
      }

      return networkResponse;
    })
    .catch(() => cachedResponse ?? new Response('', { status: 504 }));

  if (cachedResponse) {
    networkResponsePromise.catch(() => undefined);
    return cachedResponse;
  }

  return networkResponsePromise;
}
