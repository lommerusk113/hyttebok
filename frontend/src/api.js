async function request(method, url, body) {

  const isForm = body instanceof FormData
  const res = await fetch('/api' + url, {
    method,
    body: isForm ? body : body && JSON.stringify(body),
    headers: body && !isForm ? { 'Content-Type': 'application/json' } : {},
  })
  const text = await res.text()
  if (!res.ok) {
    let message = text || res.statusText
    try { message = JSON.parse(text)._embedded.errors[0].message } catch { message = `${res.status} ${res.statusText}` }
    throw new Error(message)
  }

  return text ? JSON.parse(text) : null
}

export const api = {
  get: url => request('GET', url),
  post: (url, body) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body),
  del: url => request('DELETE', url),
}

export const photoUrl = id => `/api/photos/${id}/file`

export function uploadPhoto(file, caption, uploadedBy, logEntryId) {
  const form = new FormData()
  form.append('file', file)
  if (caption) form.append('caption', caption)
  if (uploadedBy) form.append('uploadedBy', uploadedBy)
  if (logEntryId) form.append('logEntryId', logEntryId)
  return api.post('/photos', form)
}
