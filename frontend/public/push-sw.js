// Loaded into the generated service worker (vite.config.js → workbox.importScripts).
// Shows push messages from the server and opens the right page when one is tapped.

self.addEventListener('push', event => {
  const data = event.data ? event.data.json() : {}
  event.waitUntil(self.registration.showNotification(data.title || 'Hyttebok', {
    body: data.body,
    tag: data.tag,          // a newer message with the same tag replaces the old one
    icon: '/pwa-192.png',
    badge: '/mark.png',     // monochrome icon for Android's status bar
    data: { url: data.url || '/' },
  }))
})

self.addEventListener('notificationclick', event => {
  event.notification.close()
  const url = new URL(event.notification.data.url, self.location.origin).href
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(windows => {
    const open = windows.find(w => w.url.startsWith(self.location.origin))
    return open ? open.navigate(url).then(w => (w || open).focus()) : self.clients.openWindow(url)
  }))
})
