import { ref } from 'vue'

// Android/desktop Chrome hand us an install prompt; iOS has none, so we show instructions instead.
export const installPrompt = ref(null)
export const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent)
export const installed = matchMedia('(display-mode: standalone)').matches || navigator.standalone === true

addEventListener('beforeinstallprompt', e => {
  e.preventDefault()
  installPrompt.value = e
})
addEventListener('appinstalled', () => { installPrompt.value = null })

export async function install() {
  installPrompt.value.prompt()
  await installPrompt.value.userChoice
  installPrompt.value = null
}
