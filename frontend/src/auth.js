import { computed, ref } from 'vue'
import { del } from 'idb-keyval'
import { askName, userName } from './user'
import { cabinHeaders, cabinId, setAdminMode, setCabin, viewingExample } from './hytte'

// Who is logged in. /api/auth/me says whether login is on (GOOGLE_CLIENT_ID on the server) and who you are.
// Offline, the service worker (or the copy kept below) answers with the last known reply, so the app still opens at the cabin.
// For the cabin on screen: { loginEnabled, googleEnabled, user, role, admin, adminMode, canManage }
export const auth = ref(null)

export const needsLogin = computed(() => !!auth.value?.loginEnabled && !auth.value.user)
// Logged in, in a cabin, but not a member of it (and not an admin in admin mode): show "no access" instead.
export const noAccess = computed(() => !!auth.value?.user && !!cabinId.value && auth.value.forCabin === cabinId.value
  && !auth.value.role && !auth.value.adminMode && !viewingExample.value)
// The answer is about the cabin on screen (after switching cabin, a new one is on its way).
export const authReady = computed(() => !cabinId.value || auth.value?.forCabin === cabinId.value || !auth.value?.loginEnabled)

// An invitation opened before logging in waits here through Google's login, then is accepted.
const INVITE_KEY = 'hyttebok.invite'
export const rememberInvite = token => { try { localStorage.setItem(INVITE_KEY, token) } catch { /* private mode */ } }

/** Accepts an invitation for the logged-in user and makes its cabin the current one; the cabin id, or null. */
export async function acceptInvite(token) {
  try { localStorage.removeItem(INVITE_KEY) } catch { /* private mode */ }
  const res = await fetch(`/api/access/invites/${encodeURIComponent(token)}/accept`, { method: 'POST' })
  if (!res.ok) return null
  const { cabinId: joined } = await res.json()
  setCabin(joined)
  return joined
}

// The last answer is kept on the device: used when the server can't be reached (offline at the cabin).
const KEY = 'hyttebok.auth'
const RETRIES = 4
const stored = () => { try { return JSON.parse(localStorage.getItem(KEY)) } catch { return null } }

export async function loadAuth() {

  // `forCabin`: which cabin this answer is about (the one asked for, not the one on screen when it arrives),
  // so pages wait for the right one after switching. An answer for a cabin already left is dropped.
  const forCabin = cabinId.value

  // A server that's restarting answers 502 for a moment: wait and ask again rather than guess.
  for (let attempt = 0; ; attempt++) {
    try {
      const res = await fetch(forCabin ? `/api/auth/me?hytte=${forCabin}` : '/api/auth/me', { headers: cabinHeaders(forCabin) })
      if (res.ok) {
        const me = await res.json()
        if (forCabin !== cabinId.value) return // switched cabin meanwhile; that call has the newer answer
        auth.value = { ...me, forCabin }
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
  // Back from Google's login with an invitation waiting (password logins return to the invitation page itself).
  const invite = (() => { try { return localStorage.getItem(INVITE_KEY) } catch { return null } })()
  if (invite && auth.value.user && await acceptInvite(invite)) return loadAuth()

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
    setCabin(null)
    setAdminMode(false)
  } catch { /* nothing cached */ }
  location.href = '/logout'
}
