import { ref } from 'vue'
import { api } from './api'

// Shared so the hero updates when e.g. the photos tab picks a new cover image.
export const cabin = ref(null)

// Bump to open the cabin's edit panel (it lives on the home page; "Om hytta" has its own pencil too).
export const editCabin = ref(0)

export const INFO_TILES = [
  { key: 'arrivalInfo', icon: 'pi pi-sign-in', tint: 'bg-zinc-100 text-zinc-700' },
  { key: 'departureInfo', icon: 'pi pi-sign-out', tint: 'bg-zinc-100 text-zinc-700' },
  { key: 'wifi', icon: 'pi pi-wifi', tint: 'bg-zinc-100 text-zinc-700' },
  { key: 'keyInfo', icon: 'pi pi-key', tint: 'bg-zinc-100 text-zinc-700' },
  { key: 'address', icon: 'pi pi-map-marker', tint: 'bg-zinc-100 text-zinc-700' },
]

export async function loadCabin() {
  cabin.value = await api.get('/cabin')
}

export async function saveCabin(changes) {
  cabin.value = await api.put('/cabin', { ...cabin.value, ...changes })
}
