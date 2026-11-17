const prefix = "quick-bill-shell-";
const cacheName = prefix + "3";
const files = [
  "./index.html", "./styles.css", "./manage.css", "./receipt.css",
  "./app.js", "./store.js", "./money.js", "./manage.js", "./receipt.js",
  "./manifest.webmanifest", "./icons/icon-180.png", "./icons/icon-192.png",
  "./icons/icon-512.png", "./fonts/noto-sans-devanagari.woff2",
];
const shell = new URL("./index.html", self.location.href).href;

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(cacheName);
    await cache.addAll(files.map((path) => new Request(new URL(path, self.location.href), { cache: "reload" })));
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter((name) => name.startsWith(prefix) && name !== cacheName).map((name) => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET" || !event.request.url.startsWith(self.registration.scope)) return;
  event.respondWith((async () => {
    const cache = await caches.open(cacheName);
    const cached = await cache.match(event.request.mode === "navigate" ? shell : event.request);
    return cached ?? fetch(event.request);
  })());
});
