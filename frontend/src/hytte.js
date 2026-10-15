import { ref } from 'vue'

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

/** Headers for a request about `cabin` (default: the one on screen). */
export const cabinHeaders = (cabin = cabinId.value) => ({
  ...(cabin && { 'X-Hytte': String(cabin) }),
  ...(adminMode.value && { 'X-Admin-Mode': 'on' }),
})

/** "/booking?id=3" → "/h/2/booking?id=3" for the current cabin ("/" without one: Mine hytter). */
export const inCabin = path => (cabinId.value ? `/h/${cabinId.value}${path === '/' ? '' : path}` : '/')
