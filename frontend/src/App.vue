<script setup>
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePrimeVue } from 'primevue/config'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import Drawer from 'primevue/drawer'
import Popover from 'primevue/popover'
import AppMenu from './components/AppMenu.vue'
import NotificationSettings from './components/NotificationSettings.vue'
import { forgetLostTopics, loadPushState, lostTopics, restoreTopics, syncProfile } from './push'
import { userName, askName } from './user'
import { initials, colorFor } from './utils'
import { locale, primeLocale, t } from './i18n'
import { online, pending } from './offline'
import LandingPage from './components/LandingPage.vue'
import LoginPage from './components/LoginPage.vue'
import { installed } from './install'
import NoAccessPage from './components/NoAccessPage.vue'
import { auth, authReady, loadAuth, needsLogin, noAccess } from './auth'
import { cabinId, leaveExample, viewingExample } from './hytte'
import FeedbackDialog from './components/FeedbackDialog.vue'
import { forgetWeather } from './weather'

const primevue = usePrimeVue()

// The chat is on for everyone when the server has an AI key; ?chatbot=false hides it on this device (remembered),
// ?chatbot=true shows it again.
const ChatBot = defineAsyncComponent(() => import('./components/ChatBot.vue'))
const CHAT_OFF = 'hyttebok.chatbot.off'
const chatParam = new URLSearchParams(location.search).get('chatbot')
try {
  if (chatParam === 'false') localStorage.setItem(CHAT_OFF, '1')
  if (chatParam === 'true') localStorage.removeItem(CHAT_OFF)
} catch { /* private mode: the param still works for this visit */ }
const chatHidden = chatParam === 'false' || (() => { try { return localStorage.getItem(CHAT_OFF) === '1' } catch { return false } })()
const chatEnabled = computed(() => !!auth.value?.chatEnabled && !chatHidden)
// The install page (/app) is often someone's first visit; don't ask for a name there.
const route = useRoute()
const menuOpen = ref(false)
const menuPopover = ref()
const notificationsOpen = ref(false)
const feedbackOpen = ref(false)

// The example cabin's banner: out of it, to your own cabins (or to sign up, for a visitor).
function makeOwn() {
  leaveExample()
  router.push(auth.value?.user ? '/?alle=1' : '/?signup=1')
}

function logIn() {
  leaveExample()
  router.push('/?logginn=1')
}

// Where a logged-out visitor was heading (an invitation, a link from a notification): the login page shows
// first, then they're taken there, also after Google's login, which always comes back to "/".
const router = useRouter()
const RETURN_KEY = 'hyttebok.returnTo'
watch(needsLogin, locked => {
  if (locked && route.fullPath !== '/' && !viewingExample.value && route.path !== '/eksempel') {
    try { localStorage.setItem(RETURN_KEY, route.fullPath) } catch { /* private mode */ }
  }
}, { immediate: true })
watch(() => auth.value?.user, user => {
  if (!user) return
  let target = null
  try {
    target = localStorage.getItem(RETURN_KEY)
    localStorage.removeItem(RETURN_KEY)
  } catch { /* private mode */ }
  if (target && target !== route.fullPath) router.replace(target)
})

// Login state, push settings and the weather belong to the cabin on screen: (re)load them when it changes.
// Push state needs the API, so only once we know the user is let in.
async function enterCabin() {
  await loadAuth()
  if (!needsLogin.value && !noAccess.value && cabinId.value) loadPushState({ name: userName.value, lang: locale.value })
}
enterCabin()
watch(cabinId, (now, before) => {
  if (!before) return enterCabin() // first cabin of the visit: nothing cached for another one yet
  forgetWeather()
  enterCabin()
})
// The server uses the name to leave out your own changes, and the language for the texts.
watch([userName, locale], ([name, lang]) => syncProfile({ name, lang }))
const confirm = useConfirm()
const toast = useToast()

// ConfirmDialog has no dismissableMask option, so close it on backdrop clicks ourselves.
const confirmPt = { mask: { onClick: e => e.target === e.currentTarget && confirm.close() } }
const draftName = ref(userName.value)

watch(locale, () => { primevue.config.locale = primeLocale() })

// Your display name: the name others see on bookings, ticked-off items and posts. Logged in, it comes from
// your account at first login and can be changed here; it's yours only (no list of other people's names).
watch(askName, open => { if (open) draftName.value = userName.value }, { immediate: true })

async function restoreNotifications() {
  try {
    await restoreTopics({ name: userName.value, lang: locale.value })
  } catch {
    notificationsOpen.value = true // the settings panel explains what went wrong when they try there
  }
}

function saveName(name = draftName.value) {
  if (!name.trim()) return
  userName.value = name.trim()
  askName.value = false
  toast.add({ severity: 'success', summary: auth.value?.user ? t('app.nameSaved') : t('app.hello', { name: userName.value }), life: 2500 })
}
</script>

<template>
  <!-- Nothing until we know whether a login is needed, so the app doesn't flash before the login page -->
  <template v-if="!auth" />
  <!-- Invitation and new-password links work before login and before access: the pages handle both -->
  <main v-else-if="route.path.startsWith('/invitasjon/') || route.path.startsWith('/nytt-passord/') || route.path === '/personvern'" class="px-4"><RouterView /></main>
  <!-- The front page explains the app; the installed app (and deep links) go straight to logging in. The example
       cabin is open to visitors, read-only. -->
  <LandingPage v-else-if="needsLogin && route.path === '/' && !installed" />
  <LoginPage v-else-if="needsLogin && route.path !== '/app' && route.path !== '/eksempel' && !viewingExample" />
  <div v-else class="min-h-screen">
    <header class="sticky top-0 z-30 border-b border-zinc-200 bg-surface/85 text-zinc-950 backdrop-blur">
      <div class="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:h-16">
        <RouterLink to="/" class="flex min-h-11 items-center gap-2.5">
          <!-- Logo files are white; invert gives the black version for the light header -->
          <img src="/mark.png" alt="" class="h-8 w-auto invert dark:invert-0">
          <span class="flex flex-col gap-1">
            <!-- Product name first; Ravnsmark is the company behind it -->
            <span class="font-display text-base uppercase leading-none tracking-[0.18em]">Hyttebok</span>
            <span class="flex items-center gap-1 text-[10px] text-zinc-500">{{ t('app.by') }} <img src="/wordmark.png" alt="Ravnsmark" class="h-2 w-auto opacity-70 invert dark:invert-0"></span>
          </span>
        </RouterLink>

        <div class="flex items-center gap-2">
          <span v-if="!online || pending" class="flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-800" role="status">
            <i class="pi text-[10px]" :class="online ? 'pi-sync' : 'pi-wifi'" />
            {{ !online ? t('offline.offline') : '' }}{{ !online && pending ? ' · ' : '' }}{{ pending ? t('offline.pending', pending) : '' }}
          </span>
          <!-- A visitor in the example cabin: no menu (nothing of theirs to show), a way to start instead -->
          <Button v-if="!auth?.user && auth?.loginEnabled" :label="t('landing.login')" size="small" text severity="secondary" @click="logIn" />
          <!-- Wide screens: your name opens a dropdown with the same menu as the phone drawer -->
          <button v-if="auth?.user || !auth?.loginEnabled" class="hidden items-center gap-2 rounded-full py-1 pl-1 pr-3 transition hover:bg-zinc-100 sm:flex"
                  :aria-label="t('app.menu')" aria-haspopup="true" @click="menuPopover.toggle($event)">
            <img v-if="auth?.user?.picture" :src="auth.user.picture" alt="" referrerpolicy="no-referrer" class="size-8 rounded-full">
            <Avatar v-else :label="initials(userName)" shape="circle" :class="[colorFor(userName).bg, colorFor(userName).text]" class="!text-xs !font-semibold" />
            <span class="text-sm font-medium">{{ userName || t('app.whoAreYou') }}</span>
            <i class="pi pi-chevron-down text-xs text-zinc-500" />
          </button>
          <button v-if="auth?.user || !auth?.loginEnabled" class="relative -mr-2 grid size-11 place-items-center rounded-full transition hover:bg-zinc-100 sm:hidden"
                  :aria-label="t('app.menu')" :aria-expanded="menuOpen" @click="menuOpen = true">
            <i class="pi pi-bars text-lg" />
          </button>
        </div>
      </div>
    </header>

    <Drawer v-model:visible="menuOpen" position="right" :header="t('app.menu')" class="!w-[18rem]">
      <AppMenu @close="menuOpen = false" @notifications="notificationsOpen = true" @feedback="feedbackOpen = true" />
    </Drawer>
    <Popover ref="menuPopover" class="w-72">
      <AppMenu @close="menuPopover.hide()" @notifications="notificationsOpen = true" @feedback="feedbackOpen = true" />
    </Popover>

    <!-- The phone switched notifications off by itself (unused for a while): one tap to get them back -->
    <div v-if="lostTopics.length" class="border-b border-amber-200 bg-amber-50 text-amber-900" role="status">
      <div class="mx-auto flex max-w-6xl items-center gap-3 px-4 py-2 text-sm">
        <i class="pi pi-bell-slash shrink-0" />
        <span class="flex-1">{{ t('push.lost') }}</span>
        <Button :label="t('push.restore')" size="small" @click="restoreNotifications" />
        <button class="grid size-11 shrink-0 place-items-center rounded-full hover:bg-amber-100" :aria-label="t('push.dismissLost')" @click="forgetLostTopics">
          <i class="pi pi-times text-xs" />
        </button>
      </div>
    </div>

    <!-- The example cabin: made-up content, look but don't change -->
    <div v-if="viewingExample" class="border-b border-sky-200 bg-sky-50 text-sky-900" role="status">
      <div class="mx-auto flex max-w-6xl flex-wrap items-center gap-x-3 gap-y-2 px-4 py-2 text-sm">
        <i class="pi pi-eye shrink-0" />
        <span class="min-w-0 flex-1">{{ t('example.banner') }}</span>
        <Button :label="t('example.makeOwn')" size="small" @click="makeOwn" />
      </div>
    </div>

    <main class="mx-auto max-w-6xl px-4 pb-12 pt-4">
      <!-- Only inside the cabin: «Mine hytter» stays open, so a removed member can get on to their other cabins -->
      <NoAccessPage v-if="noAccess && route.params.cabinId" />
      <!-- A new cabin gets fresh pages (nothing left over from the one before) -->
      <RouterView v-else-if="!route.params.cabinId || authReady" :key="route.params.cabinId || 'home'" />
    </main>

    <FeedbackDialog v-model:visible="feedbackOpen" />

    <Drawer v-model:visible="notificationsOpen" position="right" :header="t('push.title')" class="!w-full sm:!w-[26rem]">
      <NotificationSettings />
    </Drawer>

    <Toast position="top-center" />
    <!-- Messages with an action (e.g. "Done · Undo"), shown where the thumb is -->
    <Toast group="action" position="bottom-center">
      <template #container="{ message, closeCallback }">
        <div class="flex items-center gap-3 rounded-lg bg-zinc-950 py-1 pl-4 pr-1 text-surface shadow-lg">
          <i class="pi pi-check-circle text-emerald-400" />
          <span class="flex-1 py-2 text-sm">{{ message.summary }}</span>
          <button class="min-h-11 rounded-md px-3 text-sm font-semibold hover:bg-white/10" @click="message.action(); closeCallback()">{{ message.actionLabel }}</button>
        </div>
      </template>
    </Toast>
    <ConfirmDialog :pt="confirmPt" />
    <ChatBot v-if="chatEnabled && route.path !== '/app'" />

    <Dialog dismissable-mask :visible="askName && route.path !== '/app' && !needsLogin" @update:visible="askName = $event" modal
            :header="auth?.user ? t('app.displayName') : t('app.whoAreYou')" class="w-[26rem] max-w-[calc(100vw-2rem)]">
      <p class="mb-4 text-sm text-zinc-500">{{ auth?.user ? t('app.displayNameHint', { email: auth.user.email }) : t('app.nameHint') }}</p>
      <form class="flex gap-2" @submit.prevent="saveName()">
        <InputText v-model="draftName" :placeholder="t('app.namePlaceholder')" :aria-label="t('app.displayName')" class="flex-1" maxlength="40" autofocus />
        <Button type="submit" :label="t('common.save')" :disabled="!draftName?.trim()" />
      </form>
    </Dialog>
  </div>
</template>
