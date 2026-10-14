<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
import { api } from './api'
import { locale, primeLocale, t } from './i18n'
import { online, pending } from './offline'

const primevue = usePrimeVue()
// The install page (/app) is often someone's first visit; don't ask for a name there.
const route = useRoute()
const menuOpen = ref(false)
const menuPopover = ref()
const notificationsOpen = ref(false)

loadPushState({ name: userName.value, lang: locale.value })
// The server uses the name to leave out your own changes, and the language for the texts.
watch([userName, locale], ([name, lang]) => syncProfile({ name, lang }))
const confirm = useConfirm()
const toast = useToast()

// ConfirmDialog has no dismissableMask option, so close it on backdrop clicks ourselves.
const confirmPt = { mask: { onClick: e => e.target === e.currentTarget && confirm.close() } }
const draftName = ref(userName.value)

watch(locale, () => { primevue.config.locale = primeLocale() })

// Names already in use, so the same person doesn't end up as both "Joakim" and "joakim".
const knownNames = ref([])
watch(askName, async open => {
  if (!open) return
  draftName.value = userName.value
  const [bookings, log, todos] = await Promise.all(['/bookings', '/log', '/todos'].map(api.get))
  knownNames.value = [...new Set([...bookings.map(b => b.guestName), ...log.flatMap(e => [e.author, ...e.participants]), ...todos.map(x => x.doneBy)]
    .filter(Boolean))].sort((a, b) => a.localeCompare(b))
}, { immediate: true })

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
  toast.add({ severity: 'success', summary: t('app.hello', { name: userName.value }), life: 2500 })
}
</script>

<template>
  <div class="min-h-screen">
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
          <!-- Wide screens: your name opens a dropdown with the same menu as the phone drawer -->
          <button class="hidden items-center gap-2 rounded-full py-1 pl-1 pr-3 transition hover:bg-zinc-100 sm:flex"
                  :aria-label="t('app.menu')" aria-haspopup="true" @click="menuPopover.toggle($event)">
            <Avatar :label="initials(userName)" shape="circle" :class="[colorFor(userName).bg, colorFor(userName).text]" class="!text-xs !font-semibold" />
            <span class="text-sm font-medium">{{ userName || t('app.whoAreYou') }}</span>
            <i class="pi pi-chevron-down text-xs text-zinc-500" />
          </button>
          <button class="-mr-2 grid size-11 place-items-center rounded-full transition hover:bg-zinc-100 sm:hidden"
                  :aria-label="t('app.menu')" :aria-expanded="menuOpen" @click="menuOpen = true">
            <i class="pi pi-bars text-lg" />
          </button>
        </div>
      </div>
    </header>

    <Drawer v-model:visible="menuOpen" position="right" :header="t('app.menu')" class="!w-[18rem]">
      <AppMenu @close="menuOpen = false" @notifications="notificationsOpen = true" />
    </Drawer>
    <Popover ref="menuPopover" class="w-72">
      <AppMenu @close="menuPopover.hide()" @notifications="notificationsOpen = true" />
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

    <main class="mx-auto max-w-6xl px-4 pb-12 pt-4">
      <RouterView />
    </main>

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

    <Dialog dismissable-mask :visible="askName && route.path !== '/app'" @update:visible="askName = $event" modal :header="t('app.whoAreYou')" class="w-[26rem] max-w-[calc(100vw-2rem)]">
      <p class="mb-4 text-sm text-zinc-500">{{ t('app.nameHint') }}</p>
      <div v-if="knownNames.length" class="mb-4">
        <p class="mb-2 text-sm font-medium">{{ t('app.knownNames') }}</p>
        <div class="flex flex-wrap gap-2">
          <button v-for="n in knownNames" :key="n" type="button" class="flex min-h-9 items-center gap-2 rounded-full border py-1 pl-1 pr-3 text-sm transition hover:border-zinc-400"
                  :class="n === userName ? 'border-zinc-950' : 'border-zinc-200'" @click="saveName(n)">
            <Avatar :label="initials(n)" shape="circle" size="small" :class="[colorFor(n).bg, colorFor(n).text]" class="!size-7 !text-[10px] !font-semibold" />
            {{ n }}
          </button>
        </div>
      </div>
      <p v-if="knownNames.length" class="mb-2 text-sm font-medium">{{ t('app.orTypeName') }}</p>
      <form class="flex gap-2" @submit.prevent="saveName()">
        <InputText v-model="draftName" :placeholder="t('app.namePlaceholder')" class="flex-1" :autofocus="!knownNames.length" />
        <Button type="submit" :label="t('common.save')" :disabled="!draftName?.trim()" />
      </form>
    </Dialog>
  </div>
</template>
