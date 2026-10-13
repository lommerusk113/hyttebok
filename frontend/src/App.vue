<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { usePrimeVue } from 'primevue/config'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import { useConfirm } from 'primevue/useconfirm'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import Drawer from 'primevue/drawer'
import LanguageToggle from './components/LanguageToggle.vue'
import ThemeSwitch from './components/ThemeSwitch.vue'
import { userName, askName } from './user'
import { initials, colorFor } from './utils'
import { api } from './api'
import { locale, primeLocale, t } from './i18n'
import { online, pending } from './offline'
import { install, installPrompt, installed } from './install'

const primevue = usePrimeVue()
// The install page (/app) is often someone's first visit; don't ask for a name there.
const route = useRoute()
const menuOpen = ref(false)
const confirm = useConfirm()

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

function saveName(name = draftName.value) {
  if (!name.trim()) return
  userName.value = name.trim()
  askName.value = false
}
</script>

<template>
  <div class="min-h-screen">
    <header class="sticky top-0 z-30 border-b border-zinc-200 bg-surface/85 text-zinc-950 backdrop-blur">
      <div class="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:h-16">
        <div class="flex items-center gap-4">
          <RouterLink to="/" class="flex min-h-11 items-center gap-2.5">
            <!-- Logo files are white; invert gives the black version for the light header -->
            <img src="/mark.png" alt="Ravnsmark" class="h-8 w-auto invert dark:invert-0">
            <span class="flex flex-col gap-1">
              <img src="/wordmark.png" alt="" class="h-3 w-auto invert dark:invert-0">
              <span class="text-[10px] font-medium uppercase tracking-[0.3em] text-zinc-500">{{ t('app.subtitle') }}</span>
            </span>
          </RouterLink>
          <LanguageToggle class="max-sm:!hidden" />
        </div>

        <!-- Wide screens: controls inline -->
        <div class="hidden items-center gap-3 sm:flex">
          <span v-if="!online || pending" class="flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-800" role="status">
            <i class="pi text-[10px]" :class="online ? 'pi-sync' : 'pi-wifi'" />
            {{ !online ? t('offline.offline') : '' }}{{ !online && pending ? ' · ' : '' }}{{ pending ? t('offline.pending', pending) : '' }}
          </span>
          <Button v-if="installPrompt" icon="pi pi-download" :label="t('app.install')" size="small" outlined severity="secondary" @click="install" />
          <ThemeSwitch v-tooltip.bottom="t('app.darkMode')" />
          <button class="flex items-center gap-2 rounded-full py-1 pl-1 pr-3 transition hover:bg-zinc-100"
                  :aria-label="userName || t('app.whoAreYou')" @click="askName = true">
            <Avatar :label="initials(userName)" shape="circle" :class="[colorFor(userName).bg, colorFor(userName).text]" class="!text-xs !font-semibold" />
            <span class="text-sm font-medium">{{ userName || t('app.whoAreYou') }}</span>
          </button>
        </div>

        <!-- Phones: everything behind a menu (the sync status stays visible) -->
        <div class="flex items-center gap-1 sm:hidden">
          <span v-if="!online || pending" class="flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-800" role="status">
            <i class="pi text-[10px]" :class="online ? 'pi-sync' : 'pi-wifi'" />
            {{ !online ? t('offline.offline') : '' }}{{ !online && pending ? ' · ' : '' }}{{ pending ? t('offline.pending', pending) : '' }}
          </span>
        <button class="-mr-2 grid size-11 place-items-center rounded-full transition hover:bg-zinc-100 sm:hidden"
                :aria-label="t('app.menu')" :aria-expanded="menuOpen" @click="menuOpen = true">
          <i class="pi pi-bars text-lg" />
        </button>
        </div>
      </div>
    </header>

    <Drawer v-model:visible="menuOpen" position="right" :header="t('app.menu')" class="!w-[18rem]">
      <div class="flex flex-col gap-2">
        <button class="flex min-h-14 items-center gap-3 rounded-lg border border-zinc-200 p-2 text-left transition hover:bg-zinc-50"
                @click="menuOpen = false; askName = true">
          <Avatar :label="initials(userName)" shape="circle" :class="[colorFor(userName).bg, colorFor(userName).text]" class="!font-semibold" />
          <span class="min-w-0 flex-1">
            <span class="block truncate font-medium">{{ userName || t('app.whoAreYou') }}</span>
            <span class="block text-xs text-zinc-500">{{ t('app.changeName') }}</span>
          </span>
          <i class="pi pi-chevron-right text-xs text-zinc-500" />
        </button>
        <div class="flex min-h-14 items-center justify-between gap-3 px-2">
          <span class="text-sm font-medium">{{ t('app.language') }}</span>
          <LanguageToggle />
        </div>
        <label class="flex min-h-14 cursor-pointer items-center justify-between gap-3 px-2">
          <span class="text-sm font-medium">{{ t('app.darkMode') }}</span>
          <ThemeSwitch />
        </label>

        <Button v-if="installPrompt" icon="pi pi-download" :label="t('app.install')" class="mt-2" @click="menuOpen = false; install()" />
        <RouterLink v-else-if="!installed" to="/app" class="mt-2 flex min-h-11 items-center justify-center gap-2 rounded-lg bg-zinc-100 text-sm font-medium"
                    @click="menuOpen = false">
          <i class="pi pi-mobile" />{{ t('app.install') }}
        </RouterLink>
      </div>
    </Drawer>

    <main class="mx-auto max-w-6xl px-4 pb-12 pt-4">
      <RouterView />
    </main>

    <Toast position="top-center" />
    <ConfirmDialog :pt="confirmPt" />

    <Dialog dismissable-mask :visible="askName && route.path !== '/app'" @update:visible="askName = $event" modal :header="t('app.whoAreYou')" class="w-[26rem] max-w-[calc(100vw-2rem)]">
      <p class="mb-4 text-sm text-zinc-500">{{ t('app.nameHint') }}</p>
      <div v-if="knownNames.length" class="mb-4">
        <p class="mb-2 text-xs font-medium uppercase tracking-wide text-zinc-500">{{ t('app.knownNames') }}</p>
        <div class="flex flex-wrap gap-2">
          <button v-for="n in knownNames" :key="n" type="button" class="flex min-h-9 items-center gap-2 rounded-full border py-1 pl-1 pr-3 text-sm transition hover:border-zinc-400"
                  :class="n === userName ? 'border-zinc-950' : 'border-zinc-200'" @click="saveName(n)">
            <Avatar :label="initials(n)" shape="circle" size="small" :class="[colorFor(n).bg, colorFor(n).text]" class="!size-7 !text-[10px] !font-semibold" />
            {{ n }}
          </button>
        </div>
      </div>
      <form class="flex gap-2" @submit.prevent="saveName()">
        <InputText v-model="draftName" :placeholder="t('app.namePlaceholder')" class="flex-1" autofocus />
        <Button type="submit" :label="t('common.save')" :disabled="!draftName?.trim()" />
      </form>
    </Dialog>
  </div>
</template>
