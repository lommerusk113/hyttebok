import { computed, ref } from 'vue'

// Which cabin the app is showing (from the address, /h/<id>/...), sent with every API call as "X-Hytte".
// Remembered on the device so the app opens where you left off. Admin mode (system admins only) is a
// support switch: the server then shows every cabin and lets you into any of them.
const CABIN_KEY = 'hyttebok.cabin'
const ADMIN_KEY = 'hyttebok.adminMode'
const read = key => { try { return localStorage.getItem(key) } catch { return null } }
const write = (key, value) => { try { value == null ? localStorage.removeItem(key) : localStorage.setItem(key, value) } catch { /* private mode */ } }

export const cabinId = ref(Number(read(CABIN_KEY)) || null)
export const adminMode = ref(read(ADMIN_KEY) === 'on')

export function setCabin(id) {
  cabinId.value = id ? Number(id) : null
  write(CABIN_KEY, cabinId.value)
}

export function setAdminMode(on) {
  adminMode.value = on
  write(ADMIN_KEY, on ? 'on' : null)
}

// The example cabin ("Se en eksempelhytte" on the landing page): read-only, also without logging in. For this visit
// only (sessionStorage); the cabin you were in before comes back when you leave it.
const EXAMPLE_KEY = 'hyttebok.example'
const BEFORE_KEY = 'hyttebok.beforeExample'
const session = (key, value) => {
  try {
    if (value === undefined) return sessionStorage.getItem(key)
    value == null ? sessionStorage.removeItem(key) : sessionStorage.setItem(key, value)
  } catch { /* private mode */ }
  return null
}
export const exampleCabin = ref(Number(session(EXAMPLE_KEY)) || null)
export const viewingExample = computed(() => !!exampleCabin.value && cabinId.value === exampleCabin.value)

export function enterExample(id) {
  if (!exampleCabin.value) session(BEFORE_KEY, cabinId.value)
  exampleCabin.value = Number(id)
  session(EXAMPLE_KEY, exampleCabin.value)
  cabinId.value = exampleCabin.value // not remembered on the device: the next visit opens where you were
}

export function leaveExample() {
  if (!exampleCabin.value) return
  exampleCabin.value = null
  session(EXAMPLE_KEY, null)
  setCabin(Number(session(BEFORE_KEY)) || null)
  session(BEFORE_KEY, null)
}

/** Headers for a request about `cabin` (default: the one on screen). */
export const cabinHeaders = (cabin = cabinId.value) => ({
  ...(cabin && { 'X-Hytte': String(cabin) }),
  ...(adminMode.value && { 'X-Admin-Mode': 'on' }),
})

/** "/booking?id=3" → "/h/2/booking?id=3" for the current cabin ("/" without one: Mine hytter). */
export const inCabin = path => (cabinId.value ? `/h/${cabinId.value}${path === '/' ? '' : path}` : '/')
