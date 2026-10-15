import { ref } from 'vue'
import { api } from './api'
import { cabinId } from './hytte'
import { installed, isIos } from './install'

// Push notifications for this device. The server keeps one subscription per device with the
// topics it wants; this module subscribes/unsubscribes and keeps name + language up to date.

export const TOPICS = ['BOOKING', 'POST', 'SUGGESTION', 'TODO', 'SHOPPING', 'MAINTENANCE', 'FORUM', 'ACCESS']

export const supported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
// iPhones only allow push for apps added to the home screen.
export const needsInstall = isIos && !installed
export const permission = ref(supported ? Notification.permission : 'denied')
export const topics = ref([])
// Topics this device had on, when the phone has since switched notifications off by itself
// (Android does that to apps that haven't been used for a while). Shown as a "turn back on" banner.
export const lostTopics = ref([])

// What the person chose, kept on the device, so a reset by the phone can be told apart from turning it off.
// Per cabin: each cabin has its own topics on the same device.
const key = () => `hyttebok.pushTopics.${cabinId.value}`
const chosen = () => { try { return JSON.parse(localStorage.getItem(key())) || [] } catch { return [] } }
const remember = list => { try { localStorage.setItem(key(), JSON.stringify(list)) } catch { /* private mode */ } }

/** The user may have changed it in the phone's settings meanwhile; call when showing the settings. */
export function refreshPermission() {
  if (supported) permission.value = Notification.permission
}

const urlBase64ToBytes = s => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (s.length % 4)) % 4)), c => c.charCodeAt(0))
const currentSubscription = async () => (await navigator.serviceWorker.ready).pushManager.getSubscription()
const query = endpoint => `/push/subscription?endpoint=${encodeURIComponent(endpoint)}`

export async function loadPushState(profile) {

  topics.value = [] // another cabin's choices don't carry over
  lostTopics.value = []
  if (!supported || needsInstall) return
  const subscription = await currentSubscription()
  if (subscription) {
    try {
      topics.value = (await api.get(query(subscription.endpoint))).topics
    } catch {
      topics.value = [] // the server forgot this device (e.g. the push service expired it); see below
    }
  }

  if (topics.value.length && Notification.permission === 'granted') {
    remember(topics.value) // devices set up before this was stored
    return
  }
  // Permission gone but an old subscription left behind: it can't deliver anything, so treat it as off.
  if (topics.value.length) remember(topics.value)
  topics.value = []
  const wanted = chosen()
  if (!wanted.length) return

  // Still allowed, but the subscription is gone: renew it quietly, no tap needed.
  if (Notification.permission === 'granted') {
    try {
      await setTopics(wanted, profile)
      return
    } catch { /* fall through to the banner */ }
  }
  // 'default' = the phone reset the permission and we may ask again (needs a tap).
  // 'denied' = blocked; the browser won't even show the prompt, so don't nag.
  if (Notification.permission !== 'denied') lostTopics.value = wanted
}

/** The banner's button. Must run from the tap, so the browser shows its permission prompt. */
export async function restoreTopics(profile) {
  const wanted = lostTopics.value
  lostTopics.value = []
  await setTopics(wanted, profile)
  if (!topics.value.length) remember([]) // said no to the prompt: that's their answer
}

/** The banner's close button: they don't want notifications back, so stop asking (and drop the dead subscription). */
export async function forgetLostTopics() {
  lostTopics.value = []
  remember([])
  const subscription = await currentSubscription()
  if (subscription) {
    // Only this cabin: the device's subscription may still serve other cabins.
    await api.del(query(subscription.endpoint)).catch(() => {})
  }
}

/**
 * Saves the chosen topics for this device in the current cabin; turning everything off stops this cabin's
 * notifications (the device stays subscribed for other cabins). Must start from a tap.
 */
export async function setTopics(next, { name, lang }) {

  // Ask first, while we're still inside the tap: Safari ignores the request after other awaits.
  if (next.length && Notification.permission === 'default') {
    permission.value = await Notification.requestPermission()
  }
  if (next.length && Notification.permission !== 'granted') {
    permission.value = Notification.permission
    return
  }

  let subscription = await currentSubscription()
  if (!next.length) {
    if (subscription) await api.del(query(subscription.endpoint))
    topics.value = []
    remember([])
    return
  }

  if (!subscription) {
    const { publicKey } = await api.get('/push/key')
    subscription = await (await navigator.serviceWorker.ready).pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToBytes(publicKey) })
  }
  const { endpoint, keys } = subscription.toJSON()
  await api.put('/push/subscription', { endpoint, p256dh: keys.p256dh, auth: keys.auth, name, lang, topics: next })
  topics.value = next
  remember(next)
  lostTopics.value = []
}

/** Keeps the name (so you aren't notified about your own changes) and language current. */
export async function syncProfile(profile) {
  if (topics.value.length) await setTopics(topics.value, profile)
}

export async function sendTest() {
  await api.post('/push/test', { endpoint: (await currentSubscription()).endpoint })
}
