import { ref } from 'vue'
import { get, set } from 'idb-keyval'

// Offline write queue. Changes made without a connection are stored in IndexedDB (photos included),
// shown optimistically, and replayed in order once the server is reachable. Last write wins.
// Items created offline get a negative temporary id; when the server returns the real id it is
// swapped into every later queued request (e.g. photos attached to a post written offline).

const KEY = 'hyttebok.queue'

export const online = ref(navigator.onLine)
export const pending = ref(0)
// Bumped after a sync so views refetch and swap temporary ids for real ones.
export const dataVersion = ref(0)

let queue = []
const localPhotos = new Map() // temp photo id -> object URL, so photos taken offline can be shown

const ready = get(KEY).then(stored => {
  queue = stored || []
  pending.value = queue.length
  for (const item of queue) rememberPhoto(item)
}).catch(() => {})

const persist = async () => {
  pending.value = queue.length
  await set(KEY, queue)
}

let lastTemp = 0
export const tempId = () => (lastTemp = Math.min(lastTemp - 1, -Date.now()))

export const localPhotoUrl = id => localPhotos.get(id)

function rememberPhoto(item) {
  const file = item.form?.find(([key]) => key === 'file')?.[1]
  if (file && item.tempId) localPhotos.set(item.tempId, URL.createObjectURL(file))
}

export const hasQueue = () => queue.length > 0

export async function enqueue(item) {
  await ready
  queue.push(item)
  rememberPhoto(item)
  await persist()
}

// Show not-yet-synced changes in data read from the server (or the service worker's cache).
export function applyPending(url, data) {
  for (const item of queue) {
    const [base, id, sub] = item.url.slice(1).split('/')
    if (`/${base}` !== url) continue
    if (item.method === 'POST' && !id && Array.isArray(data)) data = [item.optimistic, ...data]
    else if (item.method === 'PUT' && !id) data = Array.isArray(data) ? data.map(x => (x.id === item.optimistic.id ? item.optimistic : x)) : item.optimistic
    else if (item.method === 'PUT' && sub && Array.isArray(data)) data = data.map(x => (x.id === Number(id) ? { ...x, ...item.optimistic } : x))
    else if (item.method === 'DELETE' && Array.isArray(data)) data = data.filter(x => x.id !== Number(id))
  }

  // A queued "done" also moves the task's last-completed date (the server does that when it syncs).
  if (url === '/maintenance' && Array.isArray(data)) {
    for (const item of queue) {
      if (item.method !== 'POST' || item.url !== '/maintenance-completions') continue
      const { maintenanceId, completedOn } = item.body
      data = data.map(m => (m.id === maintenanceId && !(m.lastCompleted >= completedOn) ? { ...m, lastCompleted: completedOn } : m))
    }
  }

  return data
}

function replaceId(value, from, to) {
  if (value === from) return to
  if (value === String(from)) return String(to)
  if (Array.isArray(value)) return value.map(v => replaceId(v, from, to))
  if (value && typeof value === 'object' && !(value instanceof Blob)) {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, replaceId(v, from, to)]))
  }
  return value
}

function swapTempId(from, to) {
  queue = queue.map(item => ({
    ...item,
    url: item.url.split('/').map(seg => (seg === String(from) ? String(to) : seg)).join('/'),
    body: replaceId(item.body, from, to),
    form: replaceId(item.form, from, to),
    optimistic: replaceId(item.optimistic, from, to),
  }))
}

let syncing = false

/**
 * Sends queued changes in order. Stops at the first network/server error and retries later;
 * a 4xx (e.g. the item was deleted on the server meanwhile) drops that change.
 * Returns { sent, dropped }.
 */
export async function sync(send) {

  await ready
  if (syncing || !queue.length) return { sent: 0, dropped: 0 }

  syncing = true
  let sent = 0
  let dropped = 0
  try {
    while (queue.length) {
      const item = queue[0]
      let body = item.body
      if (item.form) {
        body = new FormData()
        for (const [key, value] of item.form) body.append(key, value)
      }

      let result
      try {
        result = await send(item.method, item.url, body, item.key)
      } catch (err) {
        if (!err.status || err.status >= 500) break
        dropped++
      }

      queue.shift()
      if (result?.id && item.tempId) swapTempId(item.tempId, result.id)
      if (result !== undefined) sent++
      await persist()
    }
  } finally {
    syncing = false
  }

  return { sent, dropped }
}

/** Runs `run` now, whenever the connection comes back, and every 30 s while changes are waiting. */
export function watchConnection(run) {
  addEventListener('online', () => { online.value = true; run() })
  addEventListener('offline', () => { online.value = false })
  setInterval(() => { if (queue.length) run() }, 30_000)
  ready.then(run)
}
