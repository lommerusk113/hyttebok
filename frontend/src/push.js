import { ref } from 'vue'
import { api } from './api'
import { installed, isIos } from './install'

// Push notifications for this device. The server keeps one subscription per device with the
// topics it wants; this module subscribes/unsubscribes and keeps name + language up to date.

export const TOPICS = ['BOOKING', 'POST', 'SUGGESTION', 'TODO', 'SHOPPING', 'MAINTENANCE']

export const supported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
// iPhones only allow push for apps added to the home screen.
export const needsInstall = isIos && !installed
export const permission = ref(supported ? Notification.permission : 'denied')
export const topics = ref([])

const urlBase64ToBytes = s => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (s.length % 4)) % 4)), c => c.charCodeAt(0))
const currentSubscription = async () => (await navigator.serviceWorker.ready).pushManager.getSubscription()
const query = endpoint => `/push/subscription?endpoint=${encodeURIComponent(endpoint)}`

export async function loadPushState() {
  if (!supported) return
  const subscription = await currentSubscription()
  if (!subscription) return
  try {
    topics.value = (await api.get(query(subscription.endpoint))).topics
  } catch {
    topics.value = [] // the server forgot this device (e.g. it was cleaned up); it can subscribe again
  }
}

/** Saves the chosen topics for this device; turning everything off unsubscribes. Must start from a tap. */
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
    if (subscription) {
      await api.del(query(subscription.endpoint))
      await subscription.unsubscribe()
    }
    topics.value = []
    return
  }

  if (!subscription) {
    const { publicKey } = await api.get('/push/key')
    subscription = await (await navigator.serviceWorker.ready).pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToBytes(publicKey) })
  }
  const { endpoint, keys } = subscription.toJSON()
  await api.put('/push/subscription', { endpoint, p256dh: keys.p256dh, auth: keys.auth, name, lang, topics: next })
  topics.value = next
}

/** Keeps the name (so you aren't notified about your own changes) and language current. */
export async function syncProfile(profile) {
  if (topics.value.length) await setTopics(topics.value, profile)
}

export async function sendTest() {
  await api.post('/push/test', { endpoint: (await currentSubscription()).endpoint })
}
