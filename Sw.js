const NAMA = 'asta-v5'; // VERSI BARU: Memaksa clear cache lama
const ASET = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(NAMA).then((c) => c.addAll(ASET)));
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((ks) => Promise.all(ks.filter((k) => k !== NAMA).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== self.location.origin || u.searchParams.has('cek')) return;
  e.respondWith(
    fetch(e.request)
      .then((r) => {
        const salinan = r.clone();
        caches.open(NAMA).then((c) => c.put(e.request, salinan));
        return r;
      })
      .catch(() => caches.match(e.request).then((r) => r || caches.match('./index.html')))
  );
});
