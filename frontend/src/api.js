import { applyPending, enqueue, hasQueue, localPhotoUrl, online, sync, tempId, watchConnection } from './offline'
import { accessChanged, sessionEnded } from './auth'
import { cabinHeaders, cabinId } from './hytte'

class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

// `cabin`: which cabin the request is about; queued offline changes keep the cabin they were made in.
async function send(method, url, body, key, cabin = cabinId.value) {

  const isForm = body instanceof FormData
  const res = await fetch('/api' + url, {
    method,
    body: isForm ? body : body && JSON.stringify(body),
    headers: {
      ...(body && !isForm && { 'Content-Type': 'application/json' }),
      ...(key && { 'Idempotency-Key': key }),
      ...cabinHeaders(cabin),
    },
  })
  const text = await res.text()
  if (res.status === 401) sessionEnded()
  if (res.status === 403) accessChanged()
  if (!res.ok) {
    let message = text || res.statusText
    try { message = JSON.parse(text)._embedded.errors[0].message } catch { message = `${res.status} ${res.statusText}` }
    throw new ApiError(message, res.status)
  }

  return text ? JSON.parse(text) : null
}

// What the UI gets back for a change that is queued instead of sent.
function optimistic(method, body, id) {

  const now = new Date().toISOString()
  if (method === 'DELETE') {
    return null
  }
  if (body instanceof FormData) {
    const fields = Object.fromEntries(body.entries())
    return { id, caption: fields.caption || null, uploadedBy: fields.uploadedBy || null, logEntryId: fields.logEntryId ? Number(fields.logEntryId) : null, forumPostId: fields.forumPostId ? Number(fields.forumPostId) : null, forumReplyId: fields.forumReplyId ? Number(fields.forumReplyId) : null, createdAt: now }
  }

  return { createdAt: now, ...body, ...(id && { id }), updatedAt: now }
}

// IndexedDB can't store Vue's reactive proxies; JSON bodies are plain data anyway.
const plain = value => (value === undefined ? undefined : JSON.parse(JSON.stringify(value)))

// The key lets the server recognise a retry of a change it already got (see IdempotencyFilter).
const newKey = () => crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`

async function queueChange(method, url, body, key) {

  const id = method === 'POST' ? tempId() : undefined
  const result = plain(optimistic(method, body, id))
  await enqueue({
    key,
    cabin: cabinId.value,
    method,
    url,
    tempId: id,
    body: body instanceof FormData ? undefined : plain(body),
    form: body instanceof FormData ? [...body.entries()] : undefined,
    optimistic: result,
  })

  return result
}

async function request(method, url, body) {

  if (method === 'GET') {
    return applyPending(url, await send(method, url), cabinId.value)
  }

  // Waiting changes go first, so a new change is queued behind them to keep the order.
  const key = newKey()
  if (online.value && !hasQueue()) {
    try {
      return await send(method, url, body, key)
    } catch (err) {
      if (err instanceof ApiError) throw err
      // ponytail: no request timeout; a hanging connection just waits. Add one if cabin signal makes that annoying.
    }
  }

  return queueChange(method, url, body, key)
}

export const api = {
  get: url => request('GET', url),
  post: (url, body) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body),
  del: url => request('DELETE', url),
}

// The browser loads images itself (no X-Hytte header), so the cabin goes in the address.
export const photoUrl = id => localPhotoUrl(id) || `/api/photos/${id}/file?hytte=${cabinId.value}`

// `links`: what the photo belongs to, e.g. { logEntryId } or { forumReplyId }.
export function uploadPhoto(file, caption, uploadedBy, links = {}) {
  const form = new FormData()
  form.append('file', file)
  if (caption) form.append('caption', caption)
  if (uploadedBy) form.append('uploadedBy', uploadedBy)
  for (const [key, id] of Object.entries(links)) if (id) form.append(key, id)
  return api.post('/photos', form)
}

/** Uploads each file on its own, so one failing doesn't lose the others. Returns { uploaded, failed } (failed: a count). */
export async function uploadEach(files, uploadedBy, links) {
  const uploaded = []
  for (const file of files) {
    try {
      uploaded.push(await uploadPhoto(file, null, uploadedBy, links))
    } catch (err) {
      console.error(err)
    }
  }
  return { uploaded, failed: files.length - uploaded.length }
}

/** Starts background syncing; `onSynced({ sent, dropped })` runs after changes were sent. */
export function startSync(onSynced) {
  watchConnection(async () => {
    const result = await sync(send)
    if (result.sent || result.dropped) onSynced(result)
  })
}
