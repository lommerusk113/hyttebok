import { ref, watch } from 'vue'

// Follows the device setting until the user picks one; index.html applies it before first paint.
const KEY = 'hyttebok.theme'
const stored = (() => { try { return localStorage.getItem(KEY) } catch { return null } })()

export const dark = ref(stored ? stored === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches)

export function setDark(on) {
  dark.value = on
  try { localStorage.setItem(KEY, dark.value ? 'dark' : 'light') } catch { /* private mode */ }
}

watch(dark, on => {
  document.documentElement.classList.toggle('app-dark', on)
  document.querySelector('meta[name=theme-color]')?.setAttribute('content', on ? '#18181b' : '#ffffff')
}, { immediate: true })
