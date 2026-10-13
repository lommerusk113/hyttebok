<script setup>
import { ref, watch } from 'vue'
import { usePrimeVue } from 'primevue/config'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import { useConfirm } from 'primevue/useconfirm'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import ToggleSwitch from 'primevue/toggleswitch'
import { userName, askName } from './user'
import { initials, colorFor } from './utils'
import { api } from './api'
import { LOCALES, locale, primeLocale, t } from './i18n'
import { dark, setDark } from './theme'

const primevue = usePrimeVue()
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
        <div class="flex items-center gap-3 sm:gap-4">
          <RouterLink to="/" class="flex items-center gap-2.5">
            <!-- Logo files are white; invert gives the black version for the light header -->
            <img src="/mark.png" alt="Ravnsmark" class="h-8 w-auto invert dark:invert-0">
            <span class="hidden flex-col gap-1 min-[380px]:flex">
              <img src="/wordmark.png" alt="" class="h-3 w-auto invert dark:invert-0">
              <span class="text-[10px] font-medium uppercase tracking-[0.3em] text-zinc-500">{{ t('app.subtitle') }}</span>
            </span>
          </RouterLink>
          <div class="flex rounded-full bg-zinc-100 p-0.5 text-xs font-semibold" role="group" :aria-label="t('app.language')">
            <button v-for="(l, code) in LOCALES" :key="code" class="rounded-full px-2.5 py-1 transition"
                    :class="locale === code ? 'bg-zinc-950 text-surface' : 'text-zinc-500 hover:text-zinc-950'" @click="locale = code">
              {{ l.label }}
            </button>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <ToggleSwitch :model-value="dark" :aria-label="t('app.darkMode')" v-tooltip.bottom="t('app.darkMode')" @update:model-value="setDark">
            <template #handle="{ checked }">
              <i class="pi !text-[10px]" :class="checked ? 'pi-moon' : 'pi-sun'" />
            </template>
          </ToggleSwitch>
          <button class="flex items-center gap-2 rounded-full py-1 pl-1 transition hover:bg-zinc-100 sm:pr-3"
                  :aria-label="userName || t('app.whoAreYou')" @click="askName = true">
            <Avatar :label="initials(userName)" shape="circle" :class="[colorFor(userName).bg, colorFor(userName).text]" class="!text-xs !font-semibold" />
            <span class="hidden text-sm font-medium sm:inline">{{ userName || t('app.whoAreYou') }}</span>
          </button>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-6xl px-4 pb-12 pt-4">
      <RouterView />
    </main>

    <Toast position="top-center" />
    <ConfirmDialog :pt="confirmPt" />

    <Dialog dismissable-mask v-model:visible="askName" modal :header="t('app.whoAreYou')" class="w-[26rem] max-w-[calc(100vw-2rem)]">
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
