import { computed, ref } from 'vue'
import { del } from 'idb-keyval'
import { askName, userName } from './user'

// Who is logged in. /api/auth/me says whether login is on (GOOGLE_CLIENT_ID on the server) and who you are.
// Offline, the service worker (or the copy kept below) answers with the last known reply, so the app still opens at the cabin.
export const auth = ref(null) // { loginEnabled, user: { name, email, picture } | null, role, admin, canManage, requestPending }

export const needsLogin = computed(() => !!auth.value?.loginEnabled && !auth.value.user)
// Logged in, but not (yet) a member of the cabin: show the "no access" page instead of the app.
export const noAccess = computed(() => !!auth.value?.user && !auth.value.role)

// An invitation opened before logging in waits here through Google's login, then is accepted.
const INVITE_KEY = 'hyttebok.invite'
export const rememberInvite = token => { try { localStorage.setItem(INVITE_KEY, token) } catch { /* private mode */ } }

/** Accepts an invitation for the logged-in user; true when it worked. */
export async function acceptInvite(token) {
  try { localStorage.removeItem(INVITE_KEY) } catch { /* private mode */ }
  const res = await fetch(`/api/access/invites/${encodeURIComponent(token)}/accept`, { method: 'POST' })
  return res.ok
}

// The last answer is kept on the device: used when the server can't be reached (offline at the cabin).
const KEY = 'hyttebok.auth'
const RETRIES = 4
const stored = () => { try { return JSON.parse(localStorage.getItem(KEY)) } catch { return null } }

export async function loadAuth() {

  // A server that's restarting answers 502 for a moment: wait and ask again rather than guess.
  for (let attempt = 0; ; attempt++) {
    try {
      const res = await fetch('/api/auth/me')
      if (res.ok) {
        auth.value = await res.json()
        try { localStorage.setItem(KEY, JSON.stringify(auth.value)) } catch { /* private mode */ }
        break
      }
    } catch { /* offline */ }
    if (attempt === RETRIES || !navigator.onLine) {
      // ponytail: never seen the server on this device = assume no login; the API still refuses anyone without one.
      auth.value = stored() || { loginEnabled: false, user: null }
      break
    }
    await new Promise(resolve => setTimeout(resolve, 1000 * (attempt + 1)))
  }
  const invite = (() => { try { return localStorage.getItem(INVITE_KEY) } catch { return null } })()
  if (invite && auth.value.user && !auth.value.role && await acceptInvite(invite)) return loadAuth()

  // First login: use the Google first name as display name (still editable in the menu).
  if (auth.value.user && !userName.value) {
    userName.value = auth.value.user.name.split(/\s+/)[0]
    askName.value = false
  }
}

/** The server says the session is gone (expired or logged out elsewhere): show the login page. */
export function sessionEnded() {
  if (auth.value?.user) auth.value = { ...auth.value, user: null }
}

/** The server refused the cabin's data (access removed while the app was open): ask again who we are. */
let checking = false
export async function accessChanged() {
  if (checking || !auth.value?.role) return
  checking = true
  await loadAuth()
  checking = false
}

export const login = () => { location.href = '/oauth/login/google' }

/** Logs out and forgets this person's data on the device (cached API answers and unsent changes). */
export async function logout() {
  try {
    await Promise.all((await caches.keys()).map(name => caches.delete(name)))
    await del('hyttebok.queue')
    localStorage.removeItem(KEY)
  } catch { /* nothing cached */ }
  location.href = '/logout'
}
