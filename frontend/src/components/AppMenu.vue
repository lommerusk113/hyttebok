<script setup>
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import LanguageToggle from './LanguageToggle.vue'
import ThemeSwitch from './ThemeSwitch.vue'
import { topics as pushTopics } from '../push'
import { userName, askName } from '../user'
import { initials, colorFor } from '../utils'
import { t } from '../i18n'
import { install, installPrompt, installed, isIos } from '../install'

// Everything personal in one place: the ☰ drawer on phones, the dropdown under your name on wide screens.
const emit = defineEmits(['close', 'notifications'])
const toast = useToast()

async function installNow() {
  emit('close')
  if (await install()) toast.add({ severity: 'success', summary: t('install.done'), detail: t('install.openFromHome'), life: 5000 })
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <button class="flex min-h-14 items-center gap-3 rounded-lg border border-zinc-200 p-2 text-left transition hover:bg-zinc-50"
            @click="emit('close'); askName = true">
      <Avatar :label="initials(userName)" shape="circle" :class="[colorFor(userName).bg, colorFor(userName).text]" class="!font-semibold" />
      <span class="min-w-0 flex-1">
        <span class="block truncate font-medium">{{ userName || t('app.whoAreYou') }}</span>
        <span class="block text-xs text-zinc-500">{{ t('app.changeName') }}</span>
      </span>
      <i class="pi pi-chevron-right text-xs text-zinc-500" />
    </button>
    <button class="flex min-h-14 items-center justify-between gap-3 px-2 text-left" @click="emit('close'); emit('notifications')">
      <span class="flex items-center gap-2 text-sm font-medium"><i class="pi pi-bell" />{{ t('push.title') }}</span>
      <span class="flex items-center gap-2 text-sm text-zinc-500">{{ pushTopics.length ? t('push.on') : t('push.off') }}<i class="pi pi-chevron-right text-xs" /></span>
    </button>
    <div class="flex min-h-14 items-center justify-between gap-3 px-2">
      <span class="text-sm font-medium">{{ t('app.language') }}</span>
      <LanguageToggle />
    </div>
    <label class="flex min-h-14 cursor-pointer items-center justify-between gap-3 px-2">
      <span class="text-sm font-medium">{{ t('app.darkMode') }}</span>
      <ThemeSwitch />
    </label>

    <!-- iPhones always go to the instructions: only Safari's Share menu can install there -->
    <Button v-if="installPrompt && !isIos" icon="pi pi-download" :label="t('app.install')" class="mt-2" @click="installNow" />
    <RouterLink v-else-if="!installed" to="/app" class="mt-2 flex min-h-11 items-center justify-center gap-2 rounded-lg bg-zinc-100 text-sm font-medium"
                @click="emit('close')">
      <i class="pi pi-mobile" />{{ t('app.install') }}
    </RouterLink>
  </div>
</template>
