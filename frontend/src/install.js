import { ref } from 'vue'

// Android/desktop Chrome hand us an install prompt; iOS has none, so we show instructions instead.
// Once the app is installed Chrome stops offering the prompt, so we remember the install ourselves
// to tell "already installed" apart from "this browser can't install".
const KEY = 'hyttebok.installed'
const remember = on => { try { on ? localStorage.setItem(KEY, '1') : localStorage.removeItem(KEY) } catch { /* private mode */ } }

export const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent)
export const installed = matchMedia('(display-mode: standalone)').matches || navigator.standalone === true
export const installPrompt = ref(null)
export const installedBefore = ref(installed || (() => { try { return localStorage.getItem(KEY) === '1' } catch { return false } })())

if (installed) remember(true)

// Android Chrome can confirm it directly (the manifest lists the app as related to itself).
navigator.getInstalledRelatedApps?.().then(apps => { if (apps.length) installedBefore.value = true }).catch(() => {})

addEventListener('beforeinstallprompt', e => {
  e.preventDefault()
  installPrompt.value = e
  // The prompt only appears when the app is NOT installed (e.g. it was removed again).
  installedBefore.value = false
  remember(false)
})

addEventListener('appinstalled', () => {
  installPrompt.value = null
  installedBefore.value = true
  remember(true)
})

/** Shows the browser's install dialog; resolves to true if the user accepted. */
export async function install() {
  const prompt = installPrompt.value
  prompt.prompt()
  const { outcome } = await prompt.userChoice
  installPrompt.value = null
  return outcome === 'accepted'
}
